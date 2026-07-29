package com.careconnect.medicalrecord;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * Clinical Records bounded context. Isolated because clinical data carries the
 * strictest access rules in the system and an append-oriented model — a
 * breach boundary worth separating physically (service-catalog.md).
 *
 * Encounters are created by EVENTS (AppointmentCompleted), never by a client
 * POST: you cannot chart a visit that didn't happen.
 */
@SpringBootApplication
@EnableFeignClients(basePackages = "com.careconnect.medicalrecord.infrastructure.client")
public class MedicalRecordServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(MedicalRecordServiceApplication.class, args);
    }
}
