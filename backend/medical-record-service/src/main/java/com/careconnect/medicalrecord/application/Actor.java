package com.careconnect.medicalrecord.application;

import java.util.UUID;

/**
 * Who is making a request, as the audit log needs to record them.
 *
 * Distinct from the ids used for authorization: {@code userId} is the identity
 * subject, whereas the ownership checks compare provider-service doctor ids and
 * patient-service patient ids (ADR-004). The account is what you hold
 * answerable, so the account is what gets logged.
 *
 * @param userId the JWT subject
 * @param role   primary role, for reading the log at a glance
 * @param email  display label, forwarded by the gateway as X-User-Email
 */
public record Actor(UUID userId, String role, String email) {
}
