package com.careconnect.gateway.security;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class InsecureDefaultsGuardTest {

    private static final String PUBLIC_DEFAULT =
            "careconnect-local-dev-secret-key-please-override-1234";
    private static final String REAL_SECRET = "a-genuinely-configured-secret-of-decent-length";

    private InsecureDefaultsGuard guard(String jwtSecret, String gatewaySecret, boolean allow) {
        return new InsecureDefaultsGuard(jwtSecret, gatewaySecret, allow);
    }

    /** The whole point: omitting configuration must stop the service, not pass. */
    @Test
    void refusesToStartOnTheRepositoryDefaultSecret() {
        assertThatThrownBy(() -> guard(PUBLIC_DEFAULT, REAL_SECRET, false).verify())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("JWT_SECRET");
    }

    @Test
    void refusesToStartOnAPublishedGatewaySecret() {
        assertThatThrownBy(() ->
                guard(REAL_SECRET, "careconnect-local-gateway-secret-change-me", false).verify())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("GATEWAY_SHARED_SECRET");
    }

    /** HS256 with under 256 bits of key is weaker than the algorithm implies. */
    @Test
    void refusesToStartOnATooShortSecret() {
        assertThatThrownBy(() -> guard("short-secret", REAL_SECRET, false).verify())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("HS256");
    }

    @Test
    void allowsTheDefaultWhenLocalDevelopmentExplicitlyOptsIn() {
        assertThatCode(() -> guard(PUBLIC_DEFAULT, REAL_SECRET, true).verify())
                .doesNotThrowAnyException();
    }

    @Test
    void staysQuietForAProperlyConfiguredGateway() {
        assertThatCode(() -> guard(REAL_SECRET, "another-real-gateway-secret", false).verify())
                .doesNotThrowAnyException();
    }

    /**
     * An unset gateway secret is a warning, not a failure — it is how tests and
     * a bare `spring-boot:run` work, and the trust filter degrades to the old
     * behaviour rather than breaking.
     */
    @Test
    void anUnsetGatewaySecretIsPermittedWithAWarning() {
        assertThatCode(() -> guard(REAL_SECRET, "", false).verify())
                .doesNotThrowAnyException();
    }
}
