package com.careconnect.provider.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.careconnect.provider.application.ProviderService;
import com.careconnect.provider.domain.Department;
import com.careconnect.provider.domain.Doctor;
import com.careconnect.provider.infrastructure.security.HeaderAuthenticationFilter;
import com.careconnect.provider.infrastructure.security.SecurityConfig;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ProviderController.class)
@Import({SecurityConfig.class, HeaderAuthenticationFilter.class})
class ProviderControllerTest {

    @Autowired MockMvc mvc;
    @MockitoBean ProviderService service;

    private Doctor doctor() {
        return new Doctor("Nisha", "Rao", "Cardiology",
                new Department("Cardiology"), new BigDecimal("800.00"));
    }

    @Test
    void directoryIsPublic() throws Exception {
        when(service.directory(any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(doctor())));

        mvc.perform(get("/api/providers/directory"))            // no auth headers at all
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].specialty").value("Cardiology"));
    }

    @Test
    void doctorCreationRequiresStaffRole() throws Exception {
        mvc.perform(post("/api/providers/doctors")
                        .header("X-User-Id", "u1").header("X-User-Roles", "DOCTOR")
                        .contentType("application/json")
                        .content("""
                                {"firstName":"A","lastName":"B","specialty":"X",
                                 "departmentId":"00000000-0000-0000-0000-000000000001",
                                 "consultationFee":100}"""))
                .andExpect(status().isForbidden());
    }

    @Test
    void anonymousManagementRequestGets401() throws Exception {
        mvc.perform(get("/api/providers/doctors/00000000-0000-0000-0000-000000000001"))
                .andExpect(status().isUnauthorized());
    }
}
