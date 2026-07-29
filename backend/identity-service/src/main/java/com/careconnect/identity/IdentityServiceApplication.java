package com.careconnect.identity;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Identity & Access bounded context (see docs/architecture/service-catalog.md).
 * Owns credentials, roles, and token lifecycles — and nothing else. Profile
 * data (patient/doctor) lives in the owning context, linked by userId.
 */
@SpringBootApplication
public class IdentityServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(IdentityServiceApplication.class, args);
    }
}
