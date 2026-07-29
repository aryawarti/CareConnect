package com.careconnect.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Single entry point for all client traffic (ADR-006).
 *
 * Responsibilities (and nothing else — business logic never lives here):
 *  - route /api/** to services resolved via Eureka (lb://)
 *  - CORS for the Angular origin
 *  - correlation-ID injection ({@link com.careconnect.gateway.filter.CorrelationIdGlobalFilter})
 *  - strip untrusted identity headers (config-repo: api-gateway.yml default-filters)
 *  - JWT validation at the edge (Phase 2)
 */
@SpringBootApplication
public class GatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(GatewayApplication.class, args);
    }
}
