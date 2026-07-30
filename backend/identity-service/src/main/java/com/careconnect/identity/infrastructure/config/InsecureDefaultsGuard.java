package com.careconnect.identity.infrastructure.config;

import com.careconnect.identity.application.JwtProperties;
import jakarta.annotation.PostConstruct;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Refuses to start with a publicly-known signing secret.
 *
 * The committed default (`careconnect-local-dev-secret-key-please-override-1234`)
 * exists so the stack runs with no setup. The problem was that it *silently*
 * kept working everywhere else: forget to set JWT_SECRET on a deployment and
 * anyone reading this repository can mint an ADMIN token. "Please override" in
 * the value is not a control.
 *
 * Fail-closed by design: the escape hatch defaults to false, so an environment
 * that never heard of it gets the strict behaviour. docker-compose opts local
 * dev in explicitly. That way the insecure path requires a deliberate act and
 * the secure path is what you get by omission — the opposite of before.
 */
@Component
public class InsecureDefaultsGuard {

    private static final Logger log = LoggerFactory.getLogger(InsecureDefaultsGuard.class);

    /** Values shipped in this repository, and therefore public knowledge. */
    private static final Set<String> KNOWN_PUBLIC_SECRETS = Set.of(
            "careconnect-local-dev-secret-key-please-override-1234",
            "change-me-generate-a-real-secret",
            "careconnect-local-gateway-secret-change-me");

    private final JwtProperties jwt;
    private final String gatewaySecret;
    private final boolean allowInsecureDefaults;

    public InsecureDefaultsGuard(
            JwtProperties jwt,
            @Value("${careconnect.platform.gateway-secret:}") String gatewaySecret,
            @Value("${careconnect.security.allow-insecure-defaults:false}")
            boolean allowInsecureDefaults) {
        this.jwt = jwt;
        this.gatewaySecret = gatewaySecret;
        this.allowInsecureDefaults = allowInsecureDefaults;
    }

    @PostConstruct
    void verify() {
        if (KNOWN_PUBLIC_SECRETS.contains(jwt.secret())) {
            reject("careconnect.jwt.secret (JWT_SECRET)");
        }
        if (!gatewaySecret.isBlank() && KNOWN_PUBLIC_SECRETS.contains(gatewaySecret)) {
            reject("careconnect.platform.gateway-secret (GATEWAY_SHARED_SECRET)");
        }
        // HS256 needs >= 256 bits of key material; a shorter secret makes the
        // signature weaker than the algorithm advertises.
        if (jwt.secret() != null && jwt.secret().length() < 32) {
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
