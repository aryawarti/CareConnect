package com.careconnect.platform.security;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * These tests encode the security property the whole authorization model rests
 * on: a service believes X-User-* only from the gateway.
 */
class GatewayTrustFilterTest {

    private static final String SECRET = "shared-secret-value";

    private HttpServletRequest passThrough(GatewayTrustFilter filter,
                                           MockHttpServletRequest request) throws Exception {
        MockFilterChain chain = new MockFilterChain();
        filter.doFilter(request, new MockHttpServletResponse(), chain);
        return (HttpServletRequest) chain.getRequest();
    }

    private MockHttpServletRequest requestClaiming(String role) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/appointments/day");
        request.addHeader("X-User-Id", "11111111-1111-1111-1111-111111111111");
        request.addHeader("X-User-Roles", role);
        request.addHeader("X-User-Email", "someone@careconnect.local");
        return request;
    }

    @Test
    void identityIsHonouredWhenTheGatewayStampIsCorrect() throws Exception {
        MockHttpServletRequest request = requestClaiming("PATIENT");
        request.addHeader(GatewayTrustFilter.GATEWAY_AUTH_HEADER, SECRET);

        HttpServletRequest seen = passThrough(new GatewayTrustFilter(SECRET), request);

        assertThat(seen.getHeader("X-User-Id"))
                .isEqualTo("11111111-1111-1111-1111-111111111111");
        assertThat(seen.getHeader("X-User-Roles")).isEqualTo("PATIENT");
    }

    /** The bypass this filter exists to stop: direct call claiming to be admin. */
    @Test
    void identityIsStrippedWhenTheStampIsAbsent() throws Exception {
        HttpServletRequest seen =
                passThrough(new GatewayTrustFilter(SECRET), requestClaiming("ADMIN"));

        assertThat(seen.getHeader("X-User-Id")).isNull();
        assertThat(seen.getHeader("X-User-Roles")).isNull();
        assertThat(seen.getHeader("X-User-Email")).isNull();
    }

    @Test
    void identityIsStrippedWhenTheStampIsWrong() throws Exception {
        MockHttpServletRequest request = requestClaiming("ADMIN");
        request.addHeader(GatewayTrustFilter.GATEWAY_AUTH_HEADER, "guessed-wrong");

        HttpServletRequest seen = passThrough(new GatewayTrustFilter(SECRET), request);

        assertThat(seen.getHeader("X-User-Id")).isNull();
    }

    /** Header names are case-insensitive over the wire; stripping must be too. */
    @Test
    void strippingIsCaseInsensitive() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/patients");
        request.addHeader("x-user-id", "11111111-1111-1111-1111-111111111111");
        request.addHeader("X-USER-ROLES", "ADMIN");

        HttpServletRequest seen = passThrough(new GatewayTrustFilter(SECRET), request);

        assertThat(seen.getHeader("X-User-Id")).isNull();
        assertThat(seen.getHeader("x-user-roles")).isNull();
    }

    /**
     * A service reading headers by enumeration must not see the stripped ones
     * either, or the protection depends on which accessor happens to be used.
     */
    @Test
    void strippedHeadersAreHiddenFromEnumerationToo() throws Exception {
        HttpServletRequest seen =
                passThrough(new GatewayTrustFilter(SECRET), requestClaiming("ADMIN"));

        List<String> names = Collections.list(seen.getHeaderNames());

        assertThat(names).noneSatisfy(name ->
                assertThat(name.toLowerCase()).startsWith("x-user-"));
        assertThat(Collections.list(seen.getHeaders("X-User-Roles"))).isEmpty();
    }

    /**
     * Unconfigured means disabled, so slice and integration tests that set
     * identity headers directly keep working. The startup warning in
     * PlatformSecurityAutoConfiguration is what stops this being silent.
     */
    @Test
    void anEmptySecretDisablesTheCheck() throws Exception {
        HttpServletRequest seen =
                passThrough(new GatewayTrustFilter(""), requestClaiming("ADMIN"));

        assertThat(seen.getHeader("X-User-Roles")).isEqualTo("ADMIN");
    }

    /** Unauthenticated traffic (container health probes) must be untouched. */
    @Test
    void requestsWithoutIdentityHeadersPassStraightThrough() throws Exception {
        MockHttpServletRequest request =
                new MockHttpServletRequest("GET", "/actuator/health/readiness");

        HttpServletRequest seen = passThrough(new GatewayTrustFilter(SECRET), request);

        assertThat(seen.getRequestURI()).isEqualTo("/actuator/health/readiness");
    }
}
