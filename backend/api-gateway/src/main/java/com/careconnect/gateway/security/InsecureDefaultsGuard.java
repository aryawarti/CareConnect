package com.careconnect.gateway.security;

import jakarta.annotation.PostConstruct;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Refuses to start with a publicly-known signing secret.
 *
 * The gateway is where a forged token would be *accepted*, so it needs this
 * check as much as identity-service does — a real secret at the minter and a
 * default at the verifier is the worse of the two mistakes.
 *
 * Duplicated rather than shared with identity-service's copy on purpose: the
 * gateway is reactive and does not depend on platform-starter, and inventing a
 * shared module to hold twenty lines would cost more than it saves. If a third
 * service ever needs this, that is the point to extract it.
 */
@Component
public class InsecureDefaultsGuard {

    private static final Logger log = LoggerFactory.getLogger(InsecureDefaultsGuard.class);

    /** Values shipped in this repository, and therefore public knowledge. */
    private static final Set<String> KNOWN_PUBLIC_SECRETS = Set.of(
            "careconnect-local-dev-secret-key-please-override-1234",
            "change-me-generate-a-real-secret",
            "careconnect-local-gateway-secret-change-me");

    private final String jwtSecret;
    private final String gatewaySecret;
    private final boolean allowInsecureDefaults;

    public InsecureDefaultsGuard(
            @Value("${careconnect.jwt.secret}") String jwtSecret,
            @Value("${careconnect.platform.gateway-secret:}") String gatewaySecret,
            @Value("${careconnect.security.allow-insecure-defaults:false}")
            boolean allowInsecureDefaults) {
        this.jwtSecret = jwtSecret;
        this.gatewaySecret = gatewaySecret;
        this.allowInsecureDefaults = allowInsecureDefaults;
    }

    @PostConstruct
    void verify() {
        if (KNOWN_PUBLIC_SECRETS.contains(jwtSecret)) {
            reject("careconnect.jwt.secret (JWT_SECRET)");
        }
        if (!gatewaySecret.isBlank() && KNOWN_PUBLIC_SECRETS.contains(gatewaySecret)) {
            reject("careconnect.platform.gateway-secret (GATEWAY_SHARED_SECRET)");
        }
        if (gatewaySecret.isBlank()) {
            log.warn("careconnect.platform.gateway-secret is not set — downstream services "
                    + "cannot distinguish gateway traffic from a direct call, so they will "
                    + "trust X-User-* headers from any caller that reaches them.");
        }
        if (jwtSecret != null && jwtSecret.length() < 32) {
            throw new IllegalStateException(
                    "careconnect.jwt.secret is shorter than 32 characters, which is below the "
                            + "minimum key length for HS256. Generate one with: openssl rand -base64 48");
        }
    }

    private void reject(String setting) {
        if (allowInsecureDefaults) {
            log.warn("{} is set to a value published in this repository. Allowed only because "
                    + "careconnect.security.allow-insecure-defaults=true. Never do this outside "
                    + "local development.", setting);
            return;
        }
        throw new IllegalStateException(
                setting + " is still the default value committed to this repository, so it is "
                        + "public knowledge and anyone could forge tokens. Set a real secret "
                        + "(openssl rand -base64 48), or set "
                        + "careconnect.security.allow-insecure-defaults=true if this really is "
                        + "local development.");
    }
}
