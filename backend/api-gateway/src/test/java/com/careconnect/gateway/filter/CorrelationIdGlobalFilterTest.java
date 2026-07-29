package com.careconnect.gateway.filter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;

class CorrelationIdGlobalFilterTest {

    private final CorrelationIdGlobalFilter filter = new CorrelationIdGlobalFilter();

    private MockServerWebExchange run(MockServerHttpRequest request) {
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        GatewayFilterChain chain = mock(GatewayFilterChain.class);
        when(chain.filter(any())).thenReturn(Mono.empty());
        filter.filter(exchange, chain).block();
        return exchange;
    }

    @Test
    void generatesCorrelationIdWhenAbsent() {
        MockServerWebExchange exchange = run(MockServerHttpRequest.get("/api/patients").build());

        String echoed = exchange.getResponse().getHeaders()
                .getFirst(CorrelationIdGlobalFilter.CORRELATION_ID_HEADER);
        assertThat(echoed).isNotBlank();
    }

    @Test
    void preservesClientProvidedCorrelationId() {
        MockServerWebExchange exchange = run(MockServerHttpRequest.get("/api/patients")
                .header(CorrelationIdGlobalFilter.CORRELATION_ID_HEADER, "client-id-123")
                .build());

        assertThat(exchange.getResponse().getHeaders()
                .getFirst(CorrelationIdGlobalFilter.CORRELATION_ID_HEADER))
                .isEqualTo("client-id-123");
    }

    @Test
    void echoesIdOnResponseForSupportability() {
        MockServerWebExchange exchange = run(MockServerHttpRequest.get("/api/x").build());

        // The response header is what lets a user report an ID we can grep across services.
        assertThat(exchange.getResponse().getHeaders()
                .getFirst(CorrelationIdGlobalFilter.CORRELATION_ID_HEADER)).isNotBlank();
    }
}
