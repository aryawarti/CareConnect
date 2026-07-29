package com.careconnect.patient;

import static org.assertj.core.api.Assertions.assertThat;

import com.careconnect.patient.api.dto.CreatePatientRequest;
import com.careconnect.patient.application.PatientService;
import com.careconnect.patient.domain.Gender;
import com.careconnect.patient.domain.Patient;
import com.careconnect.patient.domain.PatientStatus;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.data.domain.PageRequest;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
class PatientServiceIntegrationTest {

    @TestConfiguration
    static class Containers {
        @Bean
        @ServiceConnection
        PostgreSQLContainer<?> postgres() {
            return new PostgreSQLContainer<>("postgres:16-alpine");
        }
    }

    @Autowired PatientService service;

    private CreatePatientRequest req(String first, String last, String phone) {
        return new CreatePatientRequest(first, last, LocalDate.of(1985, 6, 1),
                Gender.MALE, phone, null, null, null, null, null);
    }

    @Test
    void createAssignsSequentialMrnsAndSearchFindsByNamePhoneAndMrn() {
        Patient a = service.create(req("Rahul", "Sharma", "9876500001"));
        Patient b = service.create(req("Priya", "Shah", "9876500002"));

        assertThat(a.getPatientNumber()).matches("P-\\d{6}");
        assertThat(b.getPatientNumber()).isNotEqualTo(a.getPatientNumber());

        assertThat(service.search("sha", PageRequest.of(0, 10)).getTotalElements())
                .isEqualTo(2);                                     // Sharma + Shah
        assertThat(service.search("9876500001", PageRequest.of(0, 10)).getContent())
                .extracting(Patient::getFirstName).containsExactly("Rahul");
        assertThat(service.search(a.getPatientNumber(), PageRequest.of(0, 10)).getContent())
                .hasSize(1);
    }

    @Test
    void deactivateIsSoftNeverDelete() {
        Patient p = service.create(req("Meena", "Iyer", "9876500003"));

        service.deactivate(p.getId());

        Patient reloaded = service.get(p.getId());               // still retrievable
        assertThat(reloaded.getStatus()).isEqualTo(PatientStatus.INACTIVE);
    }
}
