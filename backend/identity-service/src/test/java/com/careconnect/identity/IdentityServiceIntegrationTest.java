package com.careconnect.identity;

import static org.assertj.core.api.Assertions.assertThat;

import com.careconnect.identity.api.dto.AuthResponse;
import com.careconnect.identity.application.AuthService;
import com.careconnect.identity.domain.AuthException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.boot.test.context.TestConfiguration;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Full-context test against real Postgres (Testcontainers, ADR-008 rationale:
 * H2 would not exercise our actual migrations). Needs a running Docker daemon;
 * without one it is SKIPPED — check test counts before trusting a green build
 * (docs/engineering/testing-strategy.md).
 */
@Testcontainers(disabledWithoutDocker = true)   // skipped, not failed, when Docker is off
@SpringBootTest(properties = {
        "spring.cloud.config.enabled=false",
        "eureka.client.enabled=false",
        "careconnect.jwt.secret=test-secret-key-that-is-long-enough-for-hs256!!",
        "careconnect.jwt.access-ttl-minutes=15",
        "careconnect.jwt.refresh-ttl-days=7"
})
class IdentityServiceIntegrationTest {

    @TestConfiguration
    static class Containers {
        @Bean
        @ServiceConnection
        PostgreSQLContainer<?> postgres() {
            return new PostgreSQLContainer<>("postgres:16-alpine");
        }
    }

    @Autowired AuthService authService;

    @Test
    void registerLoginRefreshRotationWorksEndToEnd() {
        AuthResponse registered = authService.register("it@careconnect.dev", "Password123", "PATIENT");
        assertThat(registered.roles()).containsExactly("PATIENT");

        AuthResponse loggedIn = authService.login("it@careconnect.dev", "Password123");
        AuthResponse refreshed = authService.refresh(loggedIn.refreshToken());
        assertThat(refreshed.accessToken()).isNotBlank();

        // rotation: the consumed refresh token must be dead, and replaying it kills the session family
        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> authService.refresh(loggedIn.refreshToken()))
                .isInstanceOf(AuthException.class);
    }
}
