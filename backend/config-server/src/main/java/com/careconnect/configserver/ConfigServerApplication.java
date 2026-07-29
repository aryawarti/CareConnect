package com.careconnect.configserver;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.config.server.EnableConfigServer;

/**
 * Centralized configuration for all CareConnect services (ADR-006).
 *
 * Runs in "native" mode: configuration lives in {@code classpath:/config-repo},
 * versioned with the rest of the monorepo. A git-backed repo is the production
 * pattern; native keeps the monorepo self-contained (trade-off in ADR-006).
 *
 * Resolution order for a client called "api-gateway" with profile "docker":
 * application.yml -> application-docker.yml -> api-gateway.yml -> api-gateway-docker.yml
 * (later sources win).
 */
@EnableConfigServer
@SpringBootApplication
public class ConfigServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(ConfigServerApplication.class, args);
    }
}
