package com.careconnect.notification;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Notification bounded context: pure event consumer (service-catalog.md).
 * Not routed through the gateway — no client-facing API in v1. Its outage
 * must never affect business flows (NFR-2): events wait in Kafka.
 */
@SpringBootApplication
public class NotificationServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(NotificationServiceApplication.class, args);
    }
}
