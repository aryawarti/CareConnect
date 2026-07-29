package com.careconnect.medicalrecord.infrastructure.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import java.util.UUID;

/**
 * Resolves the caller's own provider-service doctor id from their identity
 * (ADR-004). Encounters are keyed by provider-service's doctor id, not the
 * identity user id, so "treating doctor" checks must resolve through here.
 */
@FeignClient(name = "provider-service", url = "${careconnect.clients.provider-url:}")
public interface ProviderClient {

    /** Caller's own doctor profile (DOCTOR role; identity headers forwarded). */
    @GetMapping("/api/providers/me")
    Envelope<MeSummary> me();

    record MeSummary(UUID id) { }

    record Envelope<T>(T data) { }
}
