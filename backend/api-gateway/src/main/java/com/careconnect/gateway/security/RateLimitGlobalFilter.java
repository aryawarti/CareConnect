package com.careconnect.gateway.security;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Per-IP throttling for the unauthenticated auth endpoints.
 *
 * Before this, {@code POST /api/auth/login} was public and unlimited: password
 * guessing was free. A token bucket per (client IP, matched rule) caps the rate
 * while leaving legitimate bursts alone — the demo seeder registers seven users
 * and logs in a dozen times within a few seconds of first start, so the budgets
 * are set well above real usage and still far below anything useful to an
 * attacker.
 *
 * Deliberate limits of this implementation, both documented rather than hidden:
 *
 *  - **In-memory, so per-instance.** Two gateway replicas would each allow the
 *    full budget. Correct for the single-gateway topology this runs in; the
 *    shared-state version needs Redis, which is not worth a datastore here.
 *  - **Rate limiting is not account lockout.** It slows guessing against the
 *    whole surface; it does not stop a slow, targeted attack on one account.
 *    That control belongs in identity-service, next to the user record, and is
 *    noted as the complementary fix.
 */
@Component
public class RateLimitGlobalFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(RateLimitGlobalFilter.class);

    /** Bounded so a flood of distinct source addresses cannot exhaust heap. */
    private static final int MAX_TRACKED_CLIENTS = 20_000;

    private final RateLimitProperties properties;
    private final AntPathMatcher matcher = new AntPathMatcher();
    private final Map<String, TokenBucket> buckets = new ConcurrentHashMap<>();

    public RateLimitGlobalFilter(RateLimitProperties properties) {
        this.properties = properties;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        if (!properties.isEnabled() || properties.getRules().isEmpty()) {
            return chain.filter(exchange);
        }
        String path = exchange.getRequest().getURI().getPath();
        for (Map.Entry<String, Integer> rule : properties.getRules().entrySet()) {
            if (matcher.match(rule.getKey(), path)) {
                return applyRule(exchange, chain, rule.getKey(), rule.getValue());
            }
        }
        return chain.filter(exchange);
    }

    private Mono<Void> applyRule(ServerWebExchange exchange, GatewayFilterChain chain,
                                 String rule, int perMinute) {
        String key = rule + "|" + clientAddress(exchange);
        sweepIfCrowded();
        TokenBucket bucket = buckets.computeIfAbsent(key, k -> new TokenBucket(perMinute));
        if (bucket.tryConsume()) {
            return chain.filter(exchange);
        }
        log.warn("rate limit hit: {} from {} ({}/min)", rule, clientAddress(exchange), perMinute);
        return tooManyRequests(exchange);
    }

    /**
     * The address to charge for this request.
     *
     * Behind the SPA's nginx, {@code X-Forwarded-For} is built with
     * {@code $proxy_add_x_forwarded_for}, which appends the peer nginx actually
     * saw to whatever the client sent. A client can prepend arbitrary entries,
     * so the only trustworthy element is the RIGHTMOST one. Taking the leftmost
     * — the usual mistake — would let an attacker rotate a fake header value on
     * every request and never be limited at all.
     */
    private String clientAddress(ServerWebExchange exchange) {
        String forwarded = exchange.getRequest().getHeaders().getFirst("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            String[] hops = forwarded.split(",");
            return hops[hops.length - 1].trim();
        }
        return exchange.getRequest().getRemoteAddress() == null
                ? "unknown"
                : exchange.getRequest().getRemoteAddress().getAddress().getHostAddress();
    }

    /** Drops buckets that have been full (i.e. idle) for a while. */
    private void sweepIfCrowded() {
        if (buckets.size() <= MAX_TRACKED_CLIENTS) {
            return;
        }
        buckets.entrySet().removeIf(entry -> entry.getValue().idle());
        if (buckets.size() > MAX_TRACKED_CLIENTS) {
            // Still oversized: an active flood. Clearing costs a brief window of
            // unthrottled traffic, which beats an OutOfMemoryError at the edge.
            log.warn("rate-limit table exceeded {} entries after sweep — clearing",
                    MAX_TRACKED_CLIENTS);
            buckets.clear();
        }
    }

    private Mono<Void> tooManyRequests(ServerWebExchange exchange) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
        response.getHeaders().setContentType(MediaType.APPLICATION_PROBLEM_JSON);
        response.getHeaders().set("Retry-After", "60");
        String body = """
                {"type":"https://careconnect.dev/errors/rate-limit",\
                "title":"Too many requests","status":429,\
                "detail":"Too many attempts from this address. Try again in a minute."}""";
        DataBuffer buffer = response.bufferFactory().wrap(body.getBytes(StandardCharsets.UTF_8));
        return response.writeWith(Mono.just(buffer));
    }

    @Override
    public int getOrder() {
        // After correlation-id assignment (so a 429 is still traceable in logs),
        // before JWT validation (so a flood is rejected before any crypto work).
        return Ordered.HIGHEST_PRECEDENCE + 1;
    }

    /**
     * Classic token bucket: {@code perMinute} tokens of capacity, refilled
     * continuously at {@code perMinute}/60 per second. Allows a short burst up
     * to capacity, then settles to the sustained rate.
     */
    private static final class TokenBucket {

        private final double capacity;
        private final double tokensPerNano;
        private double tokens;
        private long lastRefill;

        TokenBucket(int perMinute) {
            this.capacity = perMinute;
            this.tokensPerNano = perMinute / 60_000_000_000d;
            this.tokens = perMinute;
            this.lastRefill = System.nanoTime();
        }

        synchronized boolean tryConsume() {
            refill();
            if (tokens < 1d) {
                return false;
            }
            tokens -= 1d;
            return true;
        }

        /** Full bucket = nobody has spent from it recently; safe to evict. */
        synchronized boolean idle() {
            refill();
            return tokens >= capacity;
        }

        private void refill() {
            long now = System.nanoTime();
            tokens = Math.min(capacity, tokens + (now - lastRefill) * tokensPerNano);
            lastRefill = now;
        }
    }
}
