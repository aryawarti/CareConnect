package com.careconnect.gateway.security;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Per-IP request budgets for the endpoints that are reachable without a token.
 *
 * Keys are Ant path patterns, values are requests per minute (which is also the
 * burst capacity). Only the public auth endpoints are listed: they are the
 * credential-guessing surface. Authenticated traffic is deliberately not capped
 * here — a global cap would throttle the demo seeder and the SSE streams, and
 * blanket edge limiting belongs in front of the gateway (nginx, CDN, WAF) where
 * it can see real client addresses.
 */
@ConfigurationProperties(prefix = "careconnect.gateway.rate-limit")
public class RateLimitProperties {

    private boolean enabled = true;

    /** Ant pattern -> requests per minute per client IP. */
    private Map<String, Integer> rules = new LinkedHashMap<>();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Map<String, Integer> getRules() {
        return rules;
    }

    public void setRules(Map<String, Integer> rules) {
        this.rules = rules;
    }
}
