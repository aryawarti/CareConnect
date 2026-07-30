package com.careconnect.platform.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Makes "the gateway already authenticated this caller" an enforced fact rather
 * than an assumption.
 *
 * Every service authorizes from the X-User-Id / X-User-Roles headers the
 * gateway sets after validating the JWT. That model is sound only while the
 * gateway is the sole route in — and nothing in the code was enforcing it. Any
 * process that could open a TCP connection to a service port could send
 * {@code X-User-Roles: ADMIN} and be believed. (Compose published every service
 * port to the host, so that was one curl away.)
 *
 * This filter closes it: identity headers are honoured only when accompanied by
 * the shared secret that the gateway — and services making internal Feign calls
 * — attach. An unaccompanied identity header is *stripped* rather than rejected
 * with 403, so the request continues as anonymous and the existing security
 * chain answers with its normal 401. That degrades predictably and keeps
 * unauthenticated probes (actuator health checks) working untouched.
 *
 * Not a substitute for network isolation — the ports are unpublished too. This
 * is the second lock, for the day someone adds a port mapping back.
 */
public class GatewayTrustFilter extends OncePerRequestFilter implements Ordered {

    private static final Logger log = LoggerFactory.getLogger(GatewayTrustFilter.class);

    public static final String GATEWAY_AUTH_HEADER = "X-Gateway-Auth";

    /** Headers that confer identity, and therefore require proof of origin. */
    private static final Set<String> IDENTITY_HEADERS = Set.of(
            "x-user-id", "x-user-roles", "x-user-email");

    private final byte[] expectedSecret;

    public GatewayTrustFilter(String sharedSecret) {
        this.expectedSecret = sharedSecret == null ? new byte[0]
                : sharedSecret.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        if (expectedSecret.length == 0 || trusted(request)) {
            chain.doFilter(request, response);
            return;
        }
        if (request.getHeader("X-User-Id") != null) {
            // Someone reached this service directly and claimed an identity.
            log.warn("stripped unverified identity headers from {} {} (remote={}) — "
                            + "request did not come through the gateway",
                    request.getMethod(), request.getRequestURI(), request.getRemoteAddr());
        }
        chain.doFilter(new IdentityStripped(request), response);
    }

    /** Constant-time compare: a timing oracle on the secret would be free otherwise. */
    private boolean trusted(HttpServletRequest request) {
        String presented = request.getHeader(GATEWAY_AUTH_HEADER);
        if (presented == null) {
            return false;
        }
        return MessageDigest.isEqual(presented.getBytes(StandardCharsets.UTF_8), expectedSecret);
    }

    @Override
    public int getOrder() {
        // Must run before the per-service HeaderAuthenticationFilter builds a
        // SecurityContext from these headers.
        return Ordered.HIGHEST_PRECEDENCE + 6;
    }

    /** Presents the request as though the identity headers were never sent. */
    private static final class IdentityStripped extends HttpServletRequestWrapper {

        IdentityStripped(HttpServletRequest request) {
            super(request);
        }

        private static boolean hidden(String name) {
            return name != null && IDENTITY_HEADERS.contains(name.toLowerCase());
        }

        @Override
        public String getHeader(String name) {
            return hidden(name) ? null : super.getHeader(name);
        }

        @Override
        public Enumeration<String> getHeaders(String name) {
            return hidden(name) ? Collections.emptyEnumeration() : super.getHeaders(name);
        }

        @Override
        public Enumeration<String> getHeaderNames() {
            List<String> visible = Collections.list(super.getHeaderNames()).stream()
                    .filter(name -> !hidden(name))
                    .toList();
            return Collections.enumeration(visible);
        }
    }
}
