package com.careconnect.medicalrecord.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.careconnect.medicalrecord.application.MedicalRecordService;
import com.careconnect.medicalrecord.application.RecordAccessLogger;
import com.careconnect.medicalrecord.domain.Encounter;
import com.careconnect.medicalrecord.infrastructure.client.PatientClient;
import com.careconnect.medicalrecord.infrastructure.client.ProviderClient;
import com.careconnect.medicalrecord.infrastructure.security.HeaderAuthenticationFilter;
import com.careconnect.medicalrecord.infrastructure.security.SecurityConfig;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The controller collaborates with two Feign clients to resolve the caller's own
 * patient/doctor id. They must be mocked: a @WebMvcTest slice has no
 * FeignClientFactory, so without these the context fails to load and every test
 * in the class errors out before it runs.
 */
@WebMvcTest(MedicalRecordController.class)
@Import({SecurityConfig.class, HeaderAuthenticationFilter.class})
class MedicalRecordControllerTest {

    @Autowired MockMvc mvc;
    @MockitoBean MedicalRecordService service;
    @MockitoBean RecordAccessLogger accessLog;
    @MockitoBean PatientClient patientClient;
    @MockitoBean ProviderClient providerClient;

    private static final UUID ENCOUNTER = UUID.randomUUID();
    private static final String DOCTOR_ID = UUID.randomUUID().toString();

    @BeforeEach
    void identityResolves() {
        when(providerClient.me()).thenReturn(new ProviderClient.Envelope<>(
                new ProviderClient.MeSummary(UUID.fromString(DOCTOR_ID))));
        when(patientClient.me()).thenReturn(new PatientClient.Envelope<>(
                new PatientClient.MeSummary(UUID.randomUUID())));
    }

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
        when(service.getForReader(any(), any(), anyBoolean(), anyBoolean(), any()))
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

    /**
     * The reader id handed to the service must be the doctor id resolved from
     * the caller's own account, never anything they supplied. If this ever
     * regressed to passing the JWT subject or a request parameter, the
     * relationship check in the service would be comparing the wrong id space
     * and would silently pass for everyone.
     */
    @Test
    void patientHistoryAuthorizesWithTheResolvedDoctorIdNotTheCallersInput() throws Exception {
        UUID patient = UUID.randomUUID();
        when(service.forPatientAsReader(any(), any(), anyBoolean(), any(), any()))
                .thenReturn(Page.empty());

        mvc.perform(get("/api/records/patient/" + patient)
                        .header("X-User-Id", UUID.randomUUID().toString())
                        .header("X-User-Roles", "DOCTOR"))
                .andExpect(status().isOk());

        verify(service).forPatientAsReader(eq(patient), eq(UUID.fromString(DOCTOR_ID)),
                eq(false), any(), any());
    }

    /** Staff are authorized by role, so no treating relationship is required. */
    @Test
    void patientHistoryForStaffIsAuthorizedAsStaff() throws Exception {
        UUID patient = UUID.randomUUID();
        when(service.forPatientAsReader(any(), any(), anyBoolean(), any(), any()))
                .thenReturn(Page.empty());

        mvc.perform(get("/api/records/patient/" + patient)
                        .header("X-User-Id", UUID.randomUUID().toString())
                        .header("X-User-Roles", "STAFF"))
                .andExpect(status().isOk());

        verify(service).forPatientAsReader(eq(patient), any(), eq(true), any(), any());
    }

    /** A patient must not be able to read another patient's history this way. */
    @Test
    void patientRoleCannotUseTheClinicianHistoryEndpoint() throws Exception {
        mvc.perform(get("/api/records/patient/" + UUID.randomUUID())
                        .header("X-User-Id", UUID.randomUUID().toString())
                        .header("X-User-Roles", "PATIENT"))
                .andExpect(status().isForbidden());
    }
}
