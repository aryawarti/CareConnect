package com.careconnect.billing;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Billing bounded context. Separate because money has different auditors,
 * retention rules, and failure tolerance than scheduling (service-catalog.md):
 * a billing outage must never stop a clinic from seeing patients — invoices
 * simply catch up from the event log when it returns (NFR-2).
 */
@SpringBootApplication
@EnableScheduling   // drives the outbox relay (ADR-009)
@EnableFeignClients(basePackages = "com.careconnect.billing.infrastructure.client")
public class BillingServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(BillingServiceApplication.class, args);
    }
}
