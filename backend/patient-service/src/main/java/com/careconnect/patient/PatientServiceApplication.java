package com.careconnect.patient;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Patient bounded context (docs/architecture/service-catalog.md): patient
 * master data. Deliberately the first business service — its structure is
 * the template every other service copies. Annotation-minimal by policy:
 * cross-cutting config lives in infrastructure/config (Phase 2 lesson).
 */
@SpringBootApplication
@EnableScheduling   // drives the outbox relay (ADR-009)
public class PatientServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(PatientServiceApplication.class, args);
    }
}
