package com.careconnect.gateway.filter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.careconnect.gateway.security.RateLimitGlobalFilter;
import com.careconnect.gateway.security.RateLimitProperties;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;

class RateLimitGlobalFilterTest {

    private static final int BUDGET = 5;

    private RateLimitGlobalFilter filterWith(Map<String, Integer> rules, boolean enabled) {
        RateLimitProperties properties = new RateLimitProperties();
        properties.setEnabled(enabled);
        properties.setRules(rules);
        return new RateLimitGlobalFilter(properties);
    }

    private RateLimitGlobalFilter loginLimited() {
        return filterWith(Map.of("/api/auth/login", BUDGET), true);
    }

    private GatewayFilterChain passThroughChain() {
        GatewayFilterChain chain = mock(GatewayFilterChain.class);
        when(chain.filter(any())).thenReturn(Mono.empty());
        return chain;
    }

    private HttpStatus post(RateLimitGlobalFilter filter, String path, String forwardedFor) {
        MockServerHttpRequest.BaseBuilder<?> builder = MockServerHttpRequest.post(path);
        if (forwardedFor != null) {
            builder = builder.header("X-Forwarded-For", forwardedFor);
        }
        MockServerWebExchange exchange = MockServerWebExchange.from(builder.build());
        filter.filter(exchange, passThroughChain()).block();
        return (HttpStatus) exchange.getResponse().getStatusCode();
    }

    @Test
    void allowsRequestsUpToTheBudget() {
        RateLimitGlobalFilter filter = loginLimited();

        for (int attempt = 1; attempt <= BUDGET; attempt++) {
            assertThat(post(filter, "/api/auth/login", "203.0.113.7"))
                    .as("attempt %d of %d should pass", attempt, BUDGET)
                    .isNull();
        }
    }

    @Test
    void rejectsWith429OnceTheBudgetIsSpent() {
        RateLimitGlobalFilter filter = loginLimited();
        for (int i = 0; i < BUDGET; i++) {
            post(filter, "/api/auth/login", "203.0.113.7");
        }

        assertThat(post(filter, "/api/auth/login", "203.0.113.7"))
                .isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
    }

    @Test
    void tooManyRequestsCarriesProblemJsonAndRetryAfter() {
        RateLimitGlobalFilter filter = loginLimited();
        for (int i = 0; i < BUDGET; i++) {
            post(filter, "/api/auth/login", "203.0.113.7");
        }

        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/auth/login")
                        .header("X-Forwarded-For", "203.0.113.7").build());
        filter.filter(exchange, passThroughChain()).block();

        assertThat(exchange.getResponse().getHeaders().getContentType().toString())
                .isEqualTo("application/problem+json");
        assertThat(exchange.getResponse().getHeaders().getFirst("Retry-After")).isEqualTo("60");
    }

    /** One abusive client must not lock everyone else out of logging in. */
    @Test
    void budgetsAreTrackedPerClientAddress() {
        RateLimitGlobalFilter filter = loginLimited();
        for (int i = 0; i < BUDGET + 3; i++) {
            post(filter, "/api/auth/login", "203.0.113.7");
        }

        assertThat(post(filter, "/api/auth/login", "198.51.100.4")).isNull();
    }

    /**
     * nginx appends the peer it actually saw, so the rightmost entry is the only
     * trustworthy one. If the leftmost were used, a client could rotate a fake
     * value per request and never be limited — this pins that behaviour.
     */
    @Test
    void spoofedLeftmostForwardedForDoesNotEvadeTheLimit() {
        RateLimitGlobalFilter filter = loginLimited();

        for (int i = 0; i < BUDGET; i++) {
            post(filter, "/api/auth/login", "10.0.0." + i + ", 203.0.113.9");
        }

        assertThat(post(filter, "/api/auth/login", "10.9.9.9, 203.0.113.9"))
                .isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
    }

    @Test
    void unlistedPathsAreNotLimited() {
        RateLimitGlobalFilter filter = loginLimited();

        for (int i = 0; i < BUDGET * 4; i++) {
            assertThat(post(filter, "/api/appointments", "203.0.113.7")).isNull();
        }
    }

    @Test
    void disablingTheFilterRemovesAllLimits() {
        RateLimitGlobalFilter filter = filterWith(Map.of("/api/auth/login", 1), false);

        for (int i = 0; i < 10; i++) {
            assertThat(post(filter, "/api/auth/login", "203.0.113.7")).isNull();
        }
    }
}
