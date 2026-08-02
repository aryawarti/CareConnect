package com.careconnect.platform.client;

import static org.assertj.core.api.Assertions.assertThat;

import feign.FeignException;
import feign.Request;
import feign.RequestTemplate;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.client.circuitbreaker.NoFallbackAvailableException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

/**
 * The bug this guards against was invisible in every unit test that existed.
 *
 * Services catch {@code FeignException.NotFound} around their Feign calls, which
 * works perfectly when the client is a mock. In production the circuit breaker
 * substitutes {@link NoFallbackAvailableException} first, so those catches never
 * run — and a patient who had registered but not yet created a profile got HTTP
 * 500 "Unexpected error" from every screen in the application.
 */
class DownstreamFailureAdviceTest {

    private final DownstreamFailureAdvice advice = new DownstreamFailureAdvice();

    private static FeignException feignWithStatus(int status) {
        Request request = Request.create(Request.HttpMethod.GET, "/api/patients/me",
                Map.of(), new byte[0], StandardCharsets.UTF_8, new RequestTemplate());
        return FeignException.errorStatus("PatientClient#me()",
                feign.Response.builder()
                        .status(status)
                        .reason("test")
                        .request(request)
                        .headers(Map.<String, java.util.Collection<String>>of())
                        .build());
    }

    private ProblemDetail handle(Throwable cause) {
        return advice.onDownstreamFailure(
                new NoFallbackAvailableException("No fallback available.", cause));
    }

    @Test
    @DisplayName("a downstream 404 becomes 404, not 500")
    void notFoundBecomesNotFound() {
        ProblemDetail problem = handle(feignWithStatus(404));

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.NOT_FOUND.value());
        assertThat(problem.getTitle()).isEqualTo("Profile not set up");
        // The message has to tell the user what to do. "Unexpected error" — the
        // old behaviour — told them the system was broken when it was working.
        assertThat(problem.getDetail()).contains("Complete your profile");
    }

    @Test
    @DisplayName("an unreachable dependency becomes 503, as ADR-004 promises")
    void dependencyDownBecomesServiceUnavailable() {
        ProblemDetail problem = handle(new java.net.SocketTimeoutException("read timed out"));

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE.value());
        assertThat(problem.getTitle()).isEqualTo("Service temporarily unavailable");
    }

    @Test
    @DisplayName("a downstream 500 becomes 503 — their fault is our unavailability")
    void downstreamServerErrorBecomesServiceUnavailable() {
        ProblemDetail problem = handle(feignWithStatus(500));

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE.value());
    }

    @Test
    @DisplayName("a downstream 400 stays a 500 — we sent a bad request, the caller did not")
    void downstreamBadRequestIsOurBug() {
        // Reporting this as 400 would blame the user for a malformed call this
        // service constructed, and send them editing input that was already fine.
        ProblemDetail problem = handle(feignWithStatus(400));

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR.value());
    }

    @Test
    @DisplayName("an open circuit breaker (no cause at all) becomes 503")
    void nullCauseBecomesServiceUnavailable() {
        ProblemDetail problem = handle(null);

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE.value());
    }

    @Test
    @DisplayName("runs before the services' catch-all Exception handler")
    void mustOutrankTheCatchAllAdvice() {
        // Not a style assertion. Every service's ApiExceptionHandler ends with
        // @ExceptionHandler(Exception.class); advices are consulted in order and
        // the first match wins, so at equal precedence that catch-all takes
        // NoFallbackAvailableException and this class never runs. That is exactly
        // how the first attempt at this fix failed, silently, with the advice
        // registered and correct.
        Order order = AnnotationUtils.findAnnotation(DownstreamFailureAdvice.class, Order.class);

        assertThat(order).as("@Order is required, not optional").isNotNull();
        assertThat(order.value())
            .as("must outrank the services' @ExceptionHandler(Exception.class)")
            .isLessThan(Ordered.LOWEST_PRECEDENCE);
    }

    @Test
    @DisplayName("the advice handles exactly one exception type, so it shadows nothing")
    void handlesOnlyOneType() {
        // The safety argument for HIGHEST_PRECEDENCE: it can only win the types
        // it declares. If someone widens this to Exception, it would swallow
        // every service's own handlers.
        List<Class<? extends Throwable>[]> handled = java.util.Arrays.stream(
                DownstreamFailureAdvice.class.getDeclaredMethods())
            .map(m -> m.getAnnotation(org.springframework.web.bind.annotation.ExceptionHandler.class))
            .filter(java.util.Objects::nonNull)
            .map(org.springframework.web.bind.annotation.ExceptionHandler::value)
            .toList();

        assertThat(handled).hasSize(1);
        assertThat(handled.get(0)).containsExactly(NoFallbackAvailableException.class);
    }
}
