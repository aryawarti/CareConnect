package com.careconnect.identity.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.careconnect.identity.application.UserService;
import com.careconnect.identity.domain.Role;
import com.careconnect.identity.domain.User;
import com.careconnect.identity.infrastructure.security.HeaderAuthenticationFilter;
import com.careconnect.identity.infrastructure.security.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(UserController.class)
@Import({SecurityConfig.class, HeaderAuthenticationFilter.class})
class UserControllerTest {

    @Autowired MockMvc mvc;
    @MockitoBean UserService userService;

    private static final String BODY = """
            {"email":"doc@clinic.dev","password":"Password123","roles":["DOCTOR"]}""";

    @Test
    void adminCanProvisionDoctorAccount() throws Exception {
        User user = new User("doc@clinic.dev", "hash");
        user.addRole(new Role(Role.DOCTOR));
        when(userService.create(any())).thenReturn(user);

        mvc.perform(post("/api/users")
                        .header("X-User-Id", "admin-1").header("X-User-Roles", "ADMIN")
                        .contentType("application/json").content(BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.roles[0]").value("DOCTOR"));
    }

    @Test
    void staffCannotProvisionAccounts() throws Exception {
        mvc.perform(post("/api/users")
                        .header("X-User-Id", "staff-1").header("X-User-Roles", "STAFF")
                        .contentType("application/json").content(BODY))
                .andExpect(status().isForbidden());
    }

    @Test
    void anonymousGets401() throws Exception {
        mvc.perform(post("/api/users").contentType("application/json").content(BODY))
                .andExpect(status().isUnauthorized());
    }
}
