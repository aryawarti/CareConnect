package com.careconnect.gateway.filter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.careconnect.gateway.security.JwtAuthGlobalFilter;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

class JwtAuthGlobalFilterTest {

    private static final String SECRET = "test-secret-key-that-is-long-enough-for-hs256!!";

    private final JwtAuthGlobalFilter filter = new JwtAuthGlobalFilter(
            SECRET, List.of("/api/auth/**", "/api/_platform/**"));

    private String token(Instant expiry) {
        SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
        return Jwts.builder()
                .subject("11111111-1111-1111-1111-111111111111")
                .claim("roles", List.of("PATIENT"))
                .expiration(Date.from(expiry))
                .signWith(key)
                .compact();
    }

    private AtomicReference<ServerWebExchange> chainCapture(GatewayFilterChain chain) {
        AtomicReference<ServerWebExchange> forwarded = new AtomicReference<>();
        when(chain.filter(any())).thenAnswer(inv -> {
            forwarded.set(inv.getArgument(0));
            return Mono.empty();
        });
        return forwarded;
    }

    @Test
    void publicPathPassesWithoutToken() {
        GatewayFilterChain chain = mock(GatewayFilterChain.class);
        AtomicReference<ServerWebExchange> forwarded = chainCapture(chain);
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/auth/login").build());

        filter.filter(exchange, chain).block();

        assertThat(forwarded.get()).isNotNull();
        assertThat(exchange.getResponse().getStatusCode()).isNull();
    }

    @Test
    void missingTokenIsRejectedWithProblemJson() {
        GatewayFilterChain chain = mock(GatewayFilterChain.class);
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/patients").build());

        filter.filter(exchange, chain).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(exchange.getResponse().getHeaders().getContentType().toString())
                .isEqualTo("application/problem+json");
    }

    @Test
    void validTokenForwardsIdentityHeaders() {
        GatewayFilterChain chain = mock(GatewayFilterChain.class);
        AtomicReference<ServerWebExchange> forwarded = chainCapture(chain);
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/patients")
                        .header("Authorization", "Bearer " + token(Instant.now().plusSeconds(300)))
                        .build());

        filter.filter(exchange, chain).block();

        var headers = forwarded.get().getRequest().getHeaders();
        assertThat(headers.getFirst(JwtAuthGlobalFilter.USER_ID_HEADER))
                .isEqualTo("11111111-1111-1111-1111-111111111111");
        assertThat(headers.getFirst(JwtAuthGlobalFilter.ROLES_HEADER)).isEqualTo("PATIENT");
    }

    @Test
    void expiredTokenIsRejected() {
        GatewayFilterChain chain = mock(GatewayFilterChain.class);
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/patients")
                        .header("Authorization", "Bearer " + token(Instant.now().minusSeconds(60)))
                        .build());

        filter.filter(exchange, chain).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void spoofedIdentityHeadersAreOverwrittenNotTrusted() {
        GatewayFilterChain chain = mock(GatewayFilterChain.class);
        AtomicReference<ServerWebExchange> forwarded = chainCapture(chain);
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/patients")
                        .header("Authorization", "Bearer " + token(Instant.now().plusSeconds(300)))
                        .header(JwtAuthGlobalFilter.USER_ID_HEADER, "attacker-id")
                        .build());

        filter.filter(exchange, chain).block();

        assertThat(forwarded.get().getRequest().getHeaders()
                .getFirst(JwtAuthGlobalFilter.USER_ID_HEADER))
                .isEqualTo("11111111-1111-1111-1111-111111111111");
    }
}
