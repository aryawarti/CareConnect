package com.careconnect.laboratory;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Laboratory bounded context. Separate from radiology because the workflows
 * genuinely differ — specimen collection and analyser processing versus
 * modality scheduling — and the professionals and equipment are distinct.
 *
 * Two safety rules are encoded, not documented: a sample is bound to a patient
 * only by barcode scan (never by typing a name), and a result reaches the
 * patient only after a senior verifies it — but a CRITICAL value alerts the
 * ordering doctor immediately, before verification, because delay costs lives.
 */
@SpringBootApplication
@EnableScheduling
@EnableFeignClients(basePackages = "com.careconnect.laboratory.infrastructure.client")
public class LaboratoryServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(LaboratoryServiceApplication.class, args);
    }
}
