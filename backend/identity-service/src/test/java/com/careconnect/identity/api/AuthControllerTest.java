package com.careconnect.identity.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.careconnect.identity.api.dto.AuthResponse;
import com.careconnect.identity.application.AuthService;
import com.careconnect.identity.domain.AuthException;
import com.careconnect.identity.infrastructure.security.HeaderAuthenticationFilter;
import com.careconnect.identity.infrastructure.security.SecurityConfig;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AuthController.class)
@Import({SecurityConfig.class, HeaderAuthenticationFilter.class})
class AuthControllerTest {

    @Autowired MockMvc mvc;
    @MockitoBean AuthService authService;

    @Test
    void registerReturns201WithEnvelope() throws Exception {
        when(authService.register(any(), any(), any())).thenReturn(
                new AuthResponse("access", "refresh", UUID.randomUUID(),
                        "a@b.dev", List.of("PATIENT")));

        mvc.perform(post("/api/auth/register")
                        .contentType("application/json")
                        .content("""
                                {"email":"a@b.dev","password":"Password123"}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.accessToken").value("access"))
                .andExpect(jsonPath("$.data.roles[0]").value("PATIENT"));
    }

    @Test
    void weakPasswordIsRejectedAsProblemDetailWithFieldErrors() throws Exception {
        mvc.perform(post("/api/auth/register")
                        .contentType("application/json")
                        .content("""
                                {"email":"a@b.dev","password":"short"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType("application/problem+json"))
                .andExpect(jsonPath("$.title").value("Validation failed"))
                .andExpect(jsonPath("$.errors[0].field").value("password"));
    }

    @Test
    void badCredentialsMapTo401Problem() throws Exception {
        when(authService.login(any(), any())).thenThrow(new AuthException("Invalid email or password"));

        mvc.perform(post("/api/auth/login")
                        .contentType("application/json")
                        .content("""
                                {"email":"a@b.dev","password":"nope"}"""))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Invalid email or password"));
    }
}
