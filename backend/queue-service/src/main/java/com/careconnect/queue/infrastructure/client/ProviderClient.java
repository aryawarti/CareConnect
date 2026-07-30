package com.careconnect.queue.infrastructure.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import java.util.UUID;

/**
 * Resolves the caller's own provider-service doctor id from their identity.
 *
 * Needed because a DOCTOR may only open the console for their *own* queue, and
 * the doctor id in the URL is caller-supplied — it can never be trusted as
 * proof of who the caller is. The JWT subject is an identity user id, which is
 * a different id space (ADR-004), so ownership has to be resolved here.
 */
@FeignClient(name = "provider-service", url = "${careconnect.clients.provider-url:}")
public interface ProviderClient {

    /** Caller's own doctor record (DOCTOR role; identity headers forwarded). */
    @GetMapping("/api/providers/me")
    Envelope<MeSummary> me();

    /** Display name for the doctor whose queue a patient is joining. */
    @GetMapping("/api/providers/doctors/{id}/summary")
    Envelope<DoctorSummary> summary(@PathVariable("id") UUID id);

    record MeSummary(UUID id) { }

    record DoctorSummary(UUID id, String fullName, boolean active) { }

    record Envelope<T>(T data) { }
}
