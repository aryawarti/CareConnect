package com.careconnect.queue.infrastructure.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import java.util.UUID;

/**
 * Resolves the caller's own patient-service id from their identity (ADR-004:
 * we ask, we never command). Queue entries are keyed by patient-service's
 * patient id, not the identity user id, so "my place in the queue" must
 * resolve through here rather than treating the JWT subject as the patient id.
 */
@FeignClient(name = "patient-service", url = "${careconnect.clients.patient-url:}")
public interface PatientClient {

    /** Caller's own patient record (PATIENT role; identity headers forwarded). */
    @GetMapping("/api/patients/me")
    Envelope<MeSummary> me();

    record MeSummary(UUID id) { }

    record Envelope<T>(T data) { }
}
