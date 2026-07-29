package com.careconnect.discovery;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.netflix.eureka.server.EnableEurekaServer;

/**
 * Service registry (ADR-006). Services register on startup and renew a lease
 * via heartbeat (default: every 30s, evicted after 90s without renewal).
 * The gateway and Feign clients resolve logical names ("patient-service")
 * to live instances from this registry — client-side discovery.
 *
 * Dashboard: http://localhost:8761
 */
@EnableEurekaServer
@SpringBootApplication
public class DiscoveryServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(DiscoveryServerApplication.class, args);
    }
}
