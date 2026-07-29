package com.careconnect.appointment;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Scheduling bounded context — the system's core domain and collaboration hub:
 * validates against patient/provider synchronously (Feign + circuit breakers),
 * owns the appointment lifecycle, and (Phase 6) broadcasts state changes as
 * events. Chain depth is capped at 1: this service calls others; nothing it
 * calls makes further calls (docs/architecture/communication.md).
 */
@SpringBootApplication
@EnableFeignClients(basePackages = "com.careconnect.appointment.infrastructure.client")
@EnableScheduling   // drives the outbox relay (ADR-009)
public class AppointmentServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(AppointmentServiceApplication.class, args);
    }
}
