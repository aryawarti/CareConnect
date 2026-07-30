package com.careconnect.platform.client;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Propagates the caller's identity and the correlation id on every outbound
 * Feign call, and proves to the callee that the call comes from inside the
 * platform.
 *
 * Downstream services authorize with the ORIGINAL caller's identity — a service
 * never escalates privileges on anyone's behalf. That is why the identity
 * headers are copied from the inbound request rather than minted here.
 *
 * The gateway secret, by contrast, is read from configuration rather than
 * forwarded. An internal call must stand on its own as a trusted-peer call: a
 * Feign call made outside an inbound HTTP request (from a Kafka consumer, say)
 * has no headers to copy, and forwarding would silently produce an
 * unauthenticated call that fails downstream for a confusing reason.
 *
 * This lived as five byte-identical copies of a FeignConfig class, one per
 * service. It is a cross-cutting convention every service must implement the
 * same way, which is exactly what this starter is for (ADR-001).
 */
public class IdentityForwardingInterceptor implements RequestInterceptor {

    private static final String[] FORWARDED = {"X-User-Id", "X-User-Roles", "X-Correlation-Id"};
    private static final String GATEWAY_AUTH_HEADER = "X-Gateway-Auth";

    private final String gatewaySecret;

    public IdentityForwardingInterceptor(String gatewaySecret) {
        this.gatewaySecret = gatewaySecret == null ? "" : gatewaySecret;
    }

    @Override
    public void apply(RequestTemplate template) {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs) {
            HttpServletRequest request = attrs.getRequest();
            for (String header : FORWARDED) {
                String value = request.getHeader(header);
                if (value != null) {
                    template.header(header, value);
                }
            }
        }
        if (!gatewaySecret.isBlank()) {
            template.header(GATEWAY_AUTH_HEADER, gatewaySecret);
        }
    }
}
