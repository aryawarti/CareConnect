package com.careconnect.appointment;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.careconnect.appointment.api.dto.AppointmentDtos.BookRequest;
import com.careconnect.appointment.application.AppointmentService;
import com.careconnect.appointment.domain.Appointment;
import com.careconnect.appointment.domain.AppointmentConflictException;
import com.careconnect.appointment.domain.DependencyUnavailableException;
import com.github.tomakehurst.wiremock.WireMockServer;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Full-context test: real Postgres (exclusion constraint!), WireMock standing
 * in for patient/provider — the seams are the Feign clients' url property.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
class AppointmentIntegrationTest {

    static final WireMockServer WIREMOCK = new WireMockServer(0);
    static final ZoneId ZONE = ZoneId.of("Asia/Kolkata");
    static final UUID PATIENT = UUID.randomUUID();
    static final UUID DOCTOR = UUID.randomUUID();

    @TestConfiguration
    static class Containers {
        @Bean
        @ServiceConnection
        PostgreSQLContainer<?> postgres() {
            return new PostgreSQLContainer<>("postgres:16-alpine");
        }
    }

    @BeforeAll
    static void startWiremock() {
        WIREMOCK.start();
    }

    @AfterAll
    static void stopWiremock() {
        WIREMOCK.stop();
    }

    @DynamicPropertySource
    static void clients(DynamicPropertyRegistry registry) {
        registry.add("careconnect.clients.patient-url", WIREMOCK::baseUrl);
        registry.add("careconnect.clients.provider-url", WIREMOCK::baseUrl);
    }

    @Autowired AppointmentService service;

    /** Next Monday, 10:00 clinic time — always inside the stubbed Mon 09:00–13:00 window. */
    private Instant nextMondayAt10() {
        LocalDate d = LocalDate.now(ZONE).plusDays(1);
        while (d.getDayOfWeek() != DayOfWeek.MONDAY) {
            d = d.plusDays(1);
        }
        return d.atTime(LocalTime.of(10, 0)).atZone(ZONE).toInstant();
    }

    @BeforeEach
    void stubDependencies() {
        WIREMOCK.resetAll();
        WIREMOCK.stubFor(get(urlPathMatching("/api/patients/.*/summary")).willReturn(aResponse()
                .withHeader("Content-Type", "application/json")
                .withBody("""
                        {"data":{"id":"%s","active":true,"fullName":"Asha Verma"}}""".formatted(PATIENT))));
        WIREMOCK.stubFor(get(urlPathMatching("/api/providers/doctors/.*/booking-info")).willReturn(aResponse()
                .withHeader("Content-Type", "application/json")
                .withBody("""
                        {"data":{"id":"%s","active":true,"fullName":"Nisha Rao",
                         "consultationFee":800.00,"dayOff":false,
                         "windows":[{"start":"09:00","end":"13:00","slotMinutes":30}]}}""".formatted(DOCTOR))));
    }

    @Test
    void bookingSnapshotsFeeAndNames() {
        Appointment a = service.book(new BookRequest(DOCTOR, null, nextMondayAt10(), "checkup"), PATIENT);

        assertThat(a.getFeeSnapshot()).isEqualByComparingTo("800.00");
        assertThat(a.getDoctorName()).isEqualTo("Nisha Rao");
        assertThat(service.get(a.getId()).getStatus().blocksSlot()).isTrue();
    }

    @Test
    void doubleBookingIsRejectedByTheDatabaseAndCancellingFreesTheSlot() {
        Instant slot = nextMondayAt10();
        Appointment first = service.book(new BookRequest(DOCTOR, null, slot, null), PATIENT);

        assertThatThrownBy(() -> service.book(new BookRequest(DOCTOR, null, slot, null), UUID.randomUUID()))
                .isInstanceOf(AppointmentConflictException.class);

        service.cancelAsStaff(first.getId());
        Appointment rebooked = service.book(new BookRequest(DOCTOR, null, slot, null), PATIENT);
        assertThat(rebooked.getId()).isNotEqualTo(first.getId());
    }

    @Test
    void freeSlotsExcludeBookedOnes() {
        Instant slot = nextMondayAt10();
        LocalDate date = LocalDate.ofInstant(slot, ZONE);
        int before = service.freeSlots(DOCTOR, date).size();

        service.book(new BookRequest(DOCTOR, null, slot, null), PATIENT);

        assertThat(service.freeSlots(DOCTOR, date)).hasSize(before - 1)
                .noneMatch(s -> s.startAt().equals(slot));
    }

    @Test
    void bookingOutsideAvailabilityIsRejected() {
        Instant eightPm = nextMondayAt10().plusSeconds(10 * 3600);
        assertThatThrownBy(() -> service.book(new BookRequest(DOCTOR, null, eightPm, null), PATIENT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("availability");
    }

    @Test
    void providerOutageFailsFastWith503Semantics() {
        WIREMOCK.stubFor(get(urlPathMatching("/api/providers/doctors/.*/booking-info"))
                .willReturn(aResponse().withStatus(500)));

        assertThatThrownBy(() -> service.book(new BookRequest(DOCTOR, null, nextMondayAt10(), null), PATIENT))
                .isInstanceOf(DependencyUnavailableException.class);
    }
}
