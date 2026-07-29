package com.careconnect.appointment.infrastructure.client;

import java.util.UUID;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * Validation reads only (ADR-004): we ask patient-service questions; we never
 * command it. Empty url -> Eureka discovery; tests point it at WireMock.
 */
@FeignClient(name = "patient-service", url = "${careconnect.clients.patient-url:}")
public interface PatientClient {

    @GetMapping("/api/patients/{id}/summary")
    Envelope<PatientSummary> summary(@PathVariable("id") UUID id);

    /** Caller's own patient record (PATIENT role; identity headers forwarded). */
    @GetMapping("/api/patients/me")
    Envelope<MeSummary> me();

    record PatientSummary(UUID id, boolean active, String fullName) { }

    record MeSummary(UUID id) { }

    record Envelope<T>(T data) { }
}
