package com.careconnect.medicalrecord;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

import com.careconnect.medicalrecord.application.MedicalRecordService;
import com.careconnect.medicalrecord.domain.Encounter;
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
        assertThatThrownBy(() -> service.getForReader(e.getId(), UUID.randomUUID(), false, true))
                .isInstanceOf(AccessDeniedException.class);
        // the patient themselves: read allowed
        assertThat(service.getForReader(e.getId(), patient, false, true).getId()).isEqualTo(e.getId());
        // staff: read allowed
        assertThat(service.getForReader(e.getId(), UUID.randomUUID(), true, false).getId())
                .isEqualTo(e.getId());
    }
}
