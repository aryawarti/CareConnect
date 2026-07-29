package com.careconnect.medicalrecord.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.careconnect.medicalrecord.application.MedicalRecordService;
import com.careconnect.medicalrecord.domain.Encounter;
import com.careconnect.medicalrecord.infrastructure.security.HeaderAuthenticationFilter;
import com.careconnect.medicalrecord.infrastructure.security.SecurityConfig;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(MedicalRecordController.class)
@Import({SecurityConfig.class, HeaderAuthenticationFilter.class})
class MedicalRecordControllerTest {

    @Autowired MockMvc mvc;
    @MockitoBean MedicalRecordService service;

    private static final UUID ENCOUNTER = UUID.randomUUID();
    private static final String DOCTOR_ID = UUID.randomUUID().toString();

    private Encounter encounter() {
        return new Encounter(UUID.randomUUID(), UUID.randomUUID(), UUID.fromString(DOCTOR_ID),
                "Asha Verma", "Dr. Rao", Instant.now());
    }

    @Test
    void doctorCanSignOwnEncounter() throws Exception {
        when(service.sign(any(), any(), anyBoolean())).thenReturn(encounter());

        mvc.perform(post("/api/records/" + ENCOUNTER + "/signature")
                        .header("X-User-Id", DOCTOR_ID).header("X-User-Roles", "DOCTOR"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.doctorName").value("Dr. Rao"));
    }

    @Test
    void patientCannotWriteClinicalContent() throws Exception {
        mvc.perform(post("/api/records/" + ENCOUNTER + "/diagnoses")
                        .header("X-User-Id", UUID.randomUUID().toString())
                        .header("X-User-Roles", "PATIENT")
                        .contentType("application/json")
                        .content("""
                                {"code":"J06.9","description":"URI"}"""))
                .andExpect(status().isForbidden());
    }

    @Test
    void unrelatedReaderGets403FromTheServiceLayer() throws Exception {
        when(service.getForReader(any(), any(), anyBoolean(), anyBoolean()))
                .thenThrow(new AccessDeniedException("You do not have access to this record"));

        mvc.perform(get("/api/records/" + ENCOUNTER)
                        .header("X-User-Id", UUID.randomUUID().toString())
                        .header("X-User-Roles", "DOCTOR"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.title").value("Access denied"));
    }

    @Test
    void anonymousGets401() throws Exception {
        mvc.perform(get("/api/records/" + ENCOUNTER))
                .andExpect(status().isUnauthorized());
    }
}
