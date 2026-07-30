package com.careconnect.gateway.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.util.List;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Edge authentication (ADR-005): every request except the public allowlist
 * must carry a valid JWT. On success the request is enriched with
 * X-User-Id / X-User-Roles for downstream services (which trust these headers
 * because inbound copies are stripped by default-filters).
 *
 * Authentication only — no role checks here. Authorization stays in the
 * services, next to the data it protects.
 */
@Component
public class JwtAuthGlobalFilter implements GlobalFilter, Ordered {

    public static final String USER_ID_HEADER = "X-User-Id";
    public static final String ROLES_HEADER = "X-User-Roles";
    public static final String EMAIL_HEADER = "X-User-Email";
    public static final String GATEWAY_AUTH_HEADER = "X-Gateway-Auth";

    private final SecretKey key;
    private final List<String> publicPaths;
    private final String gatewaySecret;
    private final AntPathMatcher matcher = new AntPathMatcher();

    public JwtAuthGlobalFilter(
            @Value("${careconnect.jwt.secret}") String secret,
            @Value("${careconnect.gateway.public-paths}") List<String> publicPaths,
            @Value("${careconnect.platform.gateway-secret:}") String gatewaySecret) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.publicPaths = publicPaths;
        this.gatewaySecret = gatewaySecret;
    }

    /**
     * Proof to the downstream service that this request really came through the
     * gateway. Without it, services strip the identity headers below and treat
     * the caller as anonymous (see GatewayTrustFilter in platform-starter).
     */
    private void stampGatewayOrigin(HttpHeaders headers) {
        if (!gatewaySecret.isBlank()) {
            headers.set(GATEWAY_AUTH_HEADER, gatewaySecret);
        }
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();
        if (publicPaths.stream().anyMatch(p -> matcher.match(p, path))) {
            // Public route: still remove any inbound identity headers, so an
            // attacker cannot reach a service claiming to be someone.
            ServerHttpRequest sanitized = exchange.getRequest().mutate()
                    .headers(h -> {
                        h.remove(USER_ID_HEADER);
                        h.remove(ROLES_HEADER);
                        h.remove(EMAIL_HEADER);
                        stampGatewayOrigin(h);
                    })
                    .build();
            return chain.filter(exchange.mutate().request(sanitized).build());
        }

        String header = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.startsWith("Bearer ")) {
            return unauthorized(exchange, "Missing bearer token");
        }
        try {
            Claims claims = Jwts.parser().verifyWith(key).build()
                    .parseSignedClaims(header.substring(7))
                    .getPayload();
            @SuppressWarnings("unchecked")
            List<String> roles = claims.get("roles", List.class);
            // set() overwrites any spoofed inbound value — this is the ONLY
            // place these headers are produced (do not add RemoveRequestHeader
            // default-filters: route filters run after this and would erase them).
            ServerHttpRequest request = exchange.getRequest().mutate()
                    .headers(h -> {
                        h.set(USER_ID_HEADER, claims.getSubject());
                        h.set(ROLES_HEADER, roles == null ? "" : String.join(",", roles));
                        String email = claims.get("email", String.class);
                        h.set(EMAIL_HEADER, email == null ? "" : email);
                        stampGatewayOrigin(h);
                    })
                    .build();
            return chain.filter(exchange.mutate().request(request).build());
        } catch (JwtException | IllegalArgumentException e) {
            return unauthorized(exchange, "Invalid or expired token");
        }
    }

    private Mono<Void> unauthorized(ServerWebExchange exchange, String detail) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        response.getHeaders().setContentType(MediaType.APPLICATION_PROBLEM_JSON);
        String body = """
                {"type":"https://careconnect.dev/errors/authentication",\
                "title":"Authentication required","status":401,"detail":"%s"}""".formatted(detail);
        DataBuffer buffer = response.bufferFactory()
                .wrap(body.getBytes(StandardCharsets.UTF_8));
        return response.writeWith(Mono.just(buffer));
    }

    @Override
    public int getOrder() {
        // After correlation-ID assignment (+0) and rate limiting (+1), before routing.
        return Ordered.HIGHEST_PRECEDENCE + 2;
    }
}
