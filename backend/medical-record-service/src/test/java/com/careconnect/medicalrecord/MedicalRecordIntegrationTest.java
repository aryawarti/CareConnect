package com.careconnect.medicalrecord;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

import com.careconnect.medicalrecord.application.Actor;
import com.careconnect.medicalrecord.application.MedicalRecordService;
import com.careconnect.medicalrecord.application.RecordAccessLogger;
import com.careconnect.medicalrecord.domain.Encounter;
import com.careconnect.medicalrecord.domain.RecordAccessAction;
import com.careconnect.medicalrecord.infrastructure.repository.EncounterRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.data.domain.PageRequest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.security.access.AccessDeniedException;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;

/** The event -> encounter pipeline, plus the clinical access rules, end to end. */
@Testcontainers(disabledWithoutDocker = true)
@EmbeddedKafka(partitions = 1, topics = "appointment.events")
@SpringBootTest(properties = {
        "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
        "spring.kafka.consumer.auto-offset-reset=earliest",
        "spring.kafka.consumer.key-deserializer=org.apache.kafka.common.serialization.StringDeserializer",
        "spring.kafka.consumer.value-deserializer=org.apache.kafka.common.serialization.StringDeserializer",
        "spring.kafka.producer.key-serializer=org.apache.kafka.common.serialization.StringSerializer",
        "spring.kafka.producer.value-serializer=org.apache.kafka.common.serialization.StringSerializer"
})
class MedicalRecordIntegrationTest {

    @TestConfiguration
    static class Containers {
        @Bean
        @ServiceConnection
        PostgreSQLContainer<?> postgres() {
            return new PostgreSQLContainer<>("postgres:16-alpine");
        }
    }

    @Autowired KafkaTemplate<String, String> kafka;
    @Autowired EncounterRepository encounters;
    @Autowired MedicalRecordService service;
    @Autowired RecordAccessLogger accessLog;

    private String event(String eventId, String type, UUID appointmentId,
                         UUID patientId, UUID doctorId) {
        return """
                {"eventId":"%s","eventType":"%s","occurredAt":"2026-07-20T10:00:00Z",
                 "aggregateId":"%s","version":1,"correlationId":"c1",
                 "payload":{"appointmentId":"%s","patientId":"%s","doctorId":"%s",
                            "patientName":"Asha Verma","doctorName":"Dr. Rao",
                            "startAt":"2026-07-20T10:00:00Z","endAt":"2026-07-20T10:30:00Z",
                            "fee":"800.00","status":"COMPLETED"}}"""
                .formatted(eventId, type, appointmentId, appointmentId, patientId, doctorId);
    }

    @Test
    void completedAppointmentOpensAnEncounterExactlyOnce() {
        UUID appointment = UUID.randomUUID();
        UUID patient = UUID.randomUUID();
        UUID doctor = UUID.randomUUID();

        kafka.send("appointment.events", appointment.toString(),
                event(UUID.randomUUID().toString(), "AppointmentCompleted", appointment, patient, doctor));

        await().atMost(Duration.ofSeconds(20)).untilAsserted(() ->
                assertThat(encounters.existsByAppointmentId(appointment)).isTrue());

        // A *different* event id for the same appointment must not create a second chart
        kafka.send("appointment.events", appointment.toString(),
                event(UUID.randomUUID().toString(), "AppointmentCompleted", appointment, patient, doctor));
        try {
            Thread.sleep(3000);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }

        assertThat(encounters.findByPatientIdOrderByOccurredAtDesc(patient, PageRequest.of(0, 10))
                .getTotalElements()).isEqualTo(1);
    }

    @Test
    void nonCompletedAppointmentEventsDoNotCreateCharts() {
        UUID appointment = UUID.randomUUID();
        UUID patient = UUID.randomUUID();

        kafka.send("appointment.events", appointment.toString(),
                event(UUID.randomUUID().toString(), "AppointmentConfirmed",
                        appointment, patient, UUID.randomUUID()));
        try {
            Thread.sleep(3000);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }

        assertThat(encounters.existsByAppointmentId(appointment)).isFalse();
    }

    /**
     * The list endpoint, which used to have no relationship check at all: any
     * account with ROLE_DOCTOR could enumerate any patient's whole history.
     *
     * Exercises the real service and a real database — the previous coverage for
     * this guarantee stubbed the service to throw and asserted the controller
     * mapped it to 403, which proves exception mapping, not authorization.
     */
    @Test
    void aDoctorMayListAPatientHistoryOnlyIfTheyHaveTreatedThem() {
        UUID patient = UUID.randomUUID();
        UUID treatingDoctor = UUID.randomUUID();
        UUID strangerDoctor = UUID.randomUUID();
        service.openFromCompletedAppointment(UUID.randomUUID(), patient, treatingDoctor,
                "Asha Verma", "Dr. Rao", Instant.now());

        assertThat(service.forPatientAsReader(patient, treatingDoctor, false,
                PageRequest.of(0, 20), actor("DOCTOR")).getTotalElements()).isEqualTo(1);

        assertThatThrownBy(() -> service.forPatientAsReader(patient, strangerDoctor, false,
                PageRequest.of(0, 20), actor("DOCTOR")))
                .isInstanceOf(AccessDeniedException.class);
    }

    /** Reception and admin legitimately need any patient's history. */
    @Test
    void staffMayListAnyPatientHistoryWithoutHavingTreatedThem() {
        UUID patient = UUID.randomUUID();
        service.openFromCompletedAppointment(UUID.randomUUID(), patient, UUID.randomUUID(),
                "Asha Verma", "Dr. Rao", Instant.now());

        assertThat(service.forPatientAsReader(patient, UUID.randomUUID(), true,
                PageRequest.of(0, 20), actor("STAFF")).getTotalElements()).isEqualTo(1);
    }

    // ---- chart access trail --------------------------------------------------

    /** The headline guarantee: opening a chart leaves a record of who opened it. */
    @Test
    void openingAChartIsRecordedAgainstTheAccountThatOpenedIt() {
        UUID patient = UUID.randomUUID();
        UUID doctor = UUID.randomUUID();
        Encounter e = service.openFromCompletedAppointment(UUID.randomUUID(), patient, doctor,
                "Asha Verma", "Dr. Rao", Instant.now());
        Actor reader = actor("DOCTOR");

        service.getForReader(e.getId(), doctor, false, false, reader);

        var trail = accessLog.forPatient(patient, PageRequest.of(0, 10)).getContent();
        assertThat(trail).singleElement().satisfies(entry -> {
            assertThat(entry.getActorUserId()).isEqualTo(reader.userId());
            assertThat(entry.getActorRole()).isEqualTo("DOCTOR");
            assertThat(entry.getEncounterId()).isEqualTo(e.getId());
            assertThat(entry.getAction()).isEqualTo(RecordAccessAction.VIEW_ENCOUNTER);
            assertThat(entry.isSelfAccess()).isFalse();
        });
    }

    /** Listing a patient's history is a disclosure too, and is recorded as one. */
    @Test
    void listingAPatientHistoryIsRecorded() {
        UUID patient = UUID.randomUUID();
        UUID doctor = UUID.randomUUID();
        service.openFromCompletedAppointment(UUID.randomUUID(), patient, doctor,
                "Asha Verma", "Dr. Rao", Instant.now());

        service.forPatientAsReader(patient, doctor, false, PageRequest.of(0, 20), actor("DOCTOR"));

        assertThat(accessLog.forPatient(patient, PageRequest.of(0, 10)).getContent())
                .singleElement()
                .satisfies(entry -> assertThat(entry.getAction())
                        .isEqualTo(RecordAccessAction.LIST_PATIENT_HISTORY));
    }

    /**
     * A refused read discloses nothing, so it must not appear in the trail —
     * otherwise "who saw this chart" answers with people who did not.
     */
    @Test
    void aDeniedReadIsNotRecordedAsAnAccess() {
        UUID patient = UUID.randomUUID();
        Encounter e = service.openFromCompletedAppointment(UUID.randomUUID(), patient,
                UUID.randomUUID(), "Asha Verma", "Dr. Rao", Instant.now());

        assertThatThrownBy(() -> service.getForReader(e.getId(), UUID.randomUUID(), false, false,
                actor("DOCTOR")))
                .isInstanceOf(AccessDeniedException.class);

        assertThat(accessLog.forPatient(patient, PageRequest.of(0, 10)).getContent()).isEmpty();
    }

    /** A patient reading their own record is logged, and marked as self-access. */
    @Test
    void aPatientReadingTheirOwnHistoryIsLoggedAsSelfAccess() {
        UUID patient = UUID.randomUUID();
        service.openFromCompletedAppointment(UUID.randomUUID(), patient, UUID.randomUUID(),
                "Asha Verma", "Dr. Rao", Instant.now());

        service.forOwnHistory(patient, PageRequest.of(0, 20), actor("PATIENT"));

        assertThat(accessLog.forPatient(patient, PageRequest.of(0, 10)).getContent())
                .singleElement()
                .satisfies(entry -> {
                    assertThat(entry.isSelfAccess()).isTrue();
                    assertThat(entry.getAction()).isEqualTo(RecordAccessAction.LIST_OWN_HISTORY);
                });
    }

    /** "What has this account been reading" — the account-centred audit query. */
    @Test
    void theTrailCanBeQueriedByActorAcrossPatients() {
        UUID doctor = UUID.randomUUID();
        Actor reader = actor("DOCTOR");
        for (int i = 0; i < 3; i++) {
            UUID patient = UUID.randomUUID();
            Encounter e = service.openFromCompletedAppointment(UUID.randomUUID(), patient, doctor,
                    "Patient " + i, "Dr. Rao", Instant.now());
            service.getForReader(e.getId(), doctor, false, false, reader);
        }

        assertThat(accessLog.byActor(reader.userId(), PageRequest.of(0, 10)).getTotalElements())
                .isEqualTo(3);
    }

    private Actor actor(String role) {
        return new Actor(UUID.randomUUID(), role, role.toLowerCase() + "@careconnect.test");
    }

    /**
     * Treating one patient must not become a key to every patient — the check is
     * per (patient, doctor), not "is this a doctor who treats somebody".
     */
    @Test
    void treatingOnePatientGrantsNoAccessToAnother() {
        UUID doctor = UUID.randomUUID();
        UUID ownPatient = UUID.randomUUID();
        UUID otherPatient = UUID.randomUUID();
        service.openFromCompletedAppointment(UUID.randomUUID(), ownPatient, doctor,
                "Asha Verma", "Dr. Rao", Instant.now());
        service.openFromCompletedAppointment(UUID.randomUUID(), otherPatient, UUID.randomUUID(),
                "Ravi Kumar", "Dr. Iyer", Instant.now());

        assertThatThrownBy(() -> service.forPatientAsReader(otherPatient, doctor, false,
                PageRequest.of(0, 20), actor("DOCTOR")))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void onlyTheTreatingDoctorCanWriteAndOnlyOwnersCanRead() {
        UUID patient = UUID.randomUUID();
        UUID doctor = UUID.randomUUID();
        Encounter e = service.openFromCompletedAppointment(UUID.randomUUID(), patient, doctor,
                "Asha Verma", "Dr. Rao", Instant.now());

        // another doctor: no write
        assertThatThrownBy(() -> service.sign(e.getId(), UUID.randomUUID(), false))
                .isInstanceOf(AccessDeniedException.class);
        // unrelated patient: no read
        assertThatThrownBy(() -> service.getForReader(e.getId(), UUID.randomUUID(), false, true,
                actor("PATIENT")))
                .isInstanceOf(AccessDeniedException.class);
        // the patient themselves: read allowed
        assertThat(service.getForReader(e.getId(), patient, false, true, actor("PATIENT")).getId())
                .isEqualTo(e.getId());
        // staff: read allowed
        assertThat(service.getForReader(e.getId(), UUID.randomUUID(), true, false, actor("STAFF"))
                .getId()).isEqualTo(e.getId());
    }
}
