package com.careconnect.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.careconnect.provider.api.dto.DoctorDtos.CreateDoctorRequest;
import com.careconnect.provider.api.dto.DoctorDtos.ReplaceAvailabilityRequest;
import com.careconnect.provider.api.dto.DoctorDtos.SlotRequest;
import com.careconnect.provider.application.ProviderService;
import com.careconnect.provider.domain.Doctor;
import com.careconnect.provider.domain.OverlappingSlotException;
import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
class ProviderServiceIntegrationTest {

    @TestConfiguration
    static class Containers {
        @Bean
        @ServiceConnection
        PostgreSQLContainer<?> postgres() {
            return new PostgreSQLContainer<>("postgres:16-alpine");
        }
    }

    @Autowired ProviderService service;

    private Doctor newDoctor(UUID ownerUserId) {
        UUID dept = service.departments().get(0).getId();
        return service.create(new CreateDoctorRequest("Nisha", "Rao", "Cardiology",
                dept, new BigDecimal("800.00"), null, null, ownerUserId));
    }

    @Test
    void doctorAppearsInDirectoryAndSpecialtySearchWorks() {
        newDoctor(null);

        assertThat(service.directory("cardio", PageRequest.of(0, 10)).getTotalElements())
                .isGreaterThanOrEqualTo(1);
    }

    @Test
    void replaceAvailabilityValidatesOverlapsAcrossTheWholeWeek() {
        Doctor doc = newDoctor(null);
        var ok = new ReplaceAvailabilityRequest(List.of(
                new SlotRequest(1, LocalTime.of(9, 0), LocalTime.of(13, 0), 30),
                new SlotRequest(1, LocalTime.of(14, 0), LocalTime.of(17, 0), 30)));

        assertThat(service.replaceAvailability(doc.getId(), UUID.randomUUID(), true, ok)).hasSize(2);

        var overlapping = new ReplaceAvailabilityRequest(List.of(
                new SlotRequest(2, LocalTime.of(9, 0), LocalTime.of(13, 0), 30),
                new SlotRequest(2, LocalTime.of(12, 0), LocalTime.of(15, 0), 30)));
        assertThatThrownBy(() -> service.replaceAvailability(doc.getId(), UUID.randomUUID(), true, overlapping))
                .isInstanceOf(OverlappingSlotException.class);
        // failed replace must not have wiped the previous schedule (transaction rollback)
        assertThat(service.availability(doc.getId())).hasSize(2);
    }

    @Test
    void aDoctorCannotEditAnotherDoctorsSchedule() {
        UUID ownerUser = UUID.randomUUID();
        Doctor doc = newDoctor(ownerUser);
        var request = new ReplaceAvailabilityRequest(List.of(
                new SlotRequest(3, LocalTime.of(10, 0), LocalTime.of(12, 0), 20)));

        assertThat(service.replaceAvailability(doc.getId(), ownerUser, false, request)).hasSize(1);
        assertThatThrownBy(() -> service.replaceAvailability(doc.getId(), UUID.randomUUID(), false, request))
                .isInstanceOf(AccessDeniedException.class);
    }
}
