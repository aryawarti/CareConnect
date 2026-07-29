package com.careconnect.identity.application;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Bound from config-repo (identity-service.yml). The secret is HS256 —
 * shared with the gateway, which validates tokens at the edge. RS256
 * (private key here, public key at the gateway) is the upgrade path if
 * more parties ever need to verify tokens; see interview notes.
 */
@ConfigurationProperties(prefix = "careconnect.jwt")
public record JwtProperties(
        String secret,
        long accessTtlMinutes,
        long refreshTtlDays) {
}
