package com.careconnect.platform.logging;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class CorrelationIdFilterTest {

    private final CorrelationIdFilter filter = new CorrelationIdFilter();

    @Test
    void incomingCorrelationIdIsUsedAndEchoed() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(CorrelationIdFilter.HEADER, "abc-123");
        MockHttpServletResponse response = new MockHttpServletResponse();

        String[] seen = new String[1];
        FilterChain chain = (req, res) -> seen[0] = MDC.get(CorrelationIdFilter.MDC_KEY);
        filter.doFilter(request, response, chain);

        assertThat(seen[0]).isEqualTo("abc-123");
        assertThat(response.getHeader(CorrelationIdFilter.HEADER)).isEqualTo("abc-123");
    }

    @Test
    void missingCorrelationIdIsGenerated() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        String[] seen = new String[1];

        filter.doFilter(new MockHttpServletRequest(), response,
                (req, res) -> seen[0] = MDC.get(CorrelationIdFilter.MDC_KEY));

        assertThat(seen[0]).isNotBlank();
    }

    @Test
    void mdcIsClearedAfterTheRequestToPreventThreadLeakage() throws Exception {
        filter.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(),
                new MockFilterChain());

        assertThat(MDC.get(CorrelationIdFilter.MDC_KEY)).isNull();
    }
}
