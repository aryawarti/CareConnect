package com.careconnect.gateway.filter;

import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Ensures every request entering the system carries an {@code X-Correlation-Id}.
 *
 * The ID is generated here (or kept, if a client sent one), forwarded to the
 * target service, and echoed on the response so a user-visible error can be
 * matched to logs across every service it touched (NFR-5). Downstream, the
 * services put it into their logging MDC and the Feign/Kafka plumbing
 * propagates it further (docs/architecture/communication.md, "Correlation").
 */
@Component
public class CorrelationIdGlobalFilter implements GlobalFilter, Ordered {

    public static final String CORRELATION_ID_HEADER = "X-Correlation-Id";

    private static final Logger log = LoggerFactory.getLogger(CorrelationIdGlobalFilter.class);

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String incoming = exchange.getRequest().getHeaders().getFirst(CORRELATION_ID_HEADER);
        String correlationId = (incoming == null || incoming.isBlank())
                ? UUID.randomUUID().toString()
                : incoming;

        ServerHttpRequest request = exchange.getRequest().mutate()
                .headers(h -> h.set(CORRELATION_ID_HEADER, correlationId))
                .build();
        exchange.getResponse().getHeaders().set(CORRELATION_ID_HEADER, correlationId);

        if (log.isDebugEnabled()) {
            log.debug("[{}] {} {}", correlationId,
                    request.getMethod(), request.getURI().getPath());
        }
        return chain.filter(exchange.mutate().request(request).build());
    }

    @Override
    public int getOrder() {
        // Run before route filters so every downstream hop sees the header.
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
