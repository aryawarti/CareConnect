package com.careconnect.patient.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.careconnect.patient.application.PatientService;
import com.careconnect.patient.domain.Gender;
import com.careconnect.patient.domain.Patient;
import com.careconnect.patient.infrastructure.security.HeaderAuthenticationFilter;
import com.careconnect.patient.infrastructure.security.SecurityConfig;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@WebMvcTest(PatientController.class)
@Import({SecurityConfig.class, HeaderAuthenticationFilter.class})
class PatientControllerTest {

    @Autowired MockMvc mvc;
    @MockitoBean PatientService patientService;

    private static final String STAFF_ID = UUID.randomUUID().toString();
    private static final String PATIENT_USER_ID = UUID.randomUUID().toString();

    private MockHttpServletRequestBuilder asStaff(MockHttpServletRequestBuilder rb) {
        return rb.header("X-User-Id", STAFF_ID).header("X-User-Roles", "STAFF");
    }

    private MockHttpServletRequestBuilder asPatient(MockHttpServletRequestBuilder rb) {
        return rb.header("X-User-Id", PATIENT_USER_ID).header("X-User-Roles", "PATIENT");
    }

    private Patient samplePatient() {
        return new Patient("P-100001", "Asha", "Verma",
                LocalDate.of(1990, 4, 12), Gender.FEMALE);
    }

    @Test
    void staffCanSearchAndGetsPagedEnvelope() throws Exception {
        when(patientService.search(eq("ver"), any(Pageable.class)))
                .thenReturn(new PageImpl<>(java.util.List.of(samplePatient())));

        mvc.perform(asStaff(get("/api/patients").param("q", "ver")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].patientNumber").value("P-100001"))
                .andExpect(jsonPath("$.meta.totalElements").value(1));
    }

    @Test
    void patientRoleCannotSearchTheRegistry() throws Exception {
        mvc.perform(asPatient(get("/api/patients")))
                .andExpect(status().isForbidden());
    }

    @Test
    void anonymousRequestIsRejected() throws Exception {
        mvc.perform(get("/api/patients"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void patientCanFetchOwnProfileViaMe() throws Exception {
        when(patientService.getOwn(UUID.fromString(PATIENT_USER_ID)))
                .thenReturn(samplePatient());

        mvc.perform(asPatient(get("/api/patients/me")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.firstName").value("Asha"));
    }

    @Test
    void createValidatesBody() throws Exception {
        mvc.perform(asStaff(post("/api/patients")
                        .contentType("application/json")
                        .content("""
                                {"firstName":"","lastName":"X","dateOfBirth":"2999-01-01","gender":"FEMALE"}""")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Validation failed"));
    }
}
