package com.careconnect.gateway;

import com.careconnect.gateway.security.RateLimitProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/**
 * Single entry point for all client traffic (ADR-006).
 *
 * Responsibilities (and nothing else — business logic never lives here):
 *  - route /api/** to services resolved via Eureka (lb://)
 *  - CORS for the Angular origin
 *  - correlation-ID injection ({@link com.careconnect.gateway.filter.CorrelationIdGlobalFilter})
 *  - per-IP rate limiting of the public auth endpoints
 *    ({@link com.careconnect.gateway.security.RateLimitGlobalFilter})
 *  - JWT validation at the edge, replacing any inbound identity headers with
 *    ones derived from the token, and stamping the shared secret that proves to
 *    services the request came through here
 *    ({@link com.careconnect.gateway.security.JwtAuthGlobalFilter})
 *
 * Identity-header stripping happens inside that filter, NOT via
 * {@code default-filters: RemoveRequestHeader} — route filters run after global
 * filters and would delete the headers the JWT filter had just set. See the
 * note in config-repo/api-gateway.yml.
 */
@SpringBootApplication
@EnableConfigurationProperties(RateLimitProperties.class)
public class GatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(GatewayApplication.class, args);
    }
}
