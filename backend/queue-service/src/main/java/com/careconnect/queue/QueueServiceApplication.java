package com.careconnect.queue;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Live Care Flow — the real-time OPD queue.
 *
 * What makes this more than a waiting list:
 *  - every waiting patient gets a live position and an ETA computed from the
 *    doctor's own recent consultation times, not a fixed guess;
 *  - triage priority lets an emergency jump the line without erasing fairness
 *    for everyone else (they keep their relative order);
 *  - ending a consultation here is the single action that completes the
 *    appointment, opens the chart, issues the invoice and notifies the patient.
 */
@SpringBootApplication
@EnableScheduling
@EnableFeignClients(basePackages = "com.careconnect.queue.infrastructure.client")
public class QueueServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(QueueServiceApplication.class, args);
    }
}
