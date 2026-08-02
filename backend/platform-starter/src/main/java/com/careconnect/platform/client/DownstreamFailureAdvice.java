package com.careconnect.platform.client;

import feign.FeignException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.client.circuitbreaker.NoFallbackAvailableException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;

/**
 * Translates a failed service-to-service call into the status the caller deserves.
 *
 * <h2>Why this is needed at all</h2>
 *
 * Spring Cloud's circuit breaker wraps a Feign client. When no fallback factory is
 * registered — which is the case for every client here, deliberately, because
 * ADR-004 says synchronous calls fail fast rather than serving invented data — any
 * exception the call throws is replaced by {@link NoFallbackAvailableException},
 * with the original as its cause.
 *
 * That replacement is easy to miss, and it silently defeats the obvious defence.
 * Services had written, reasonably:
 *
 * <pre>
 *     try { return patientClient.me().data().id(); }
 *     catch (FeignException.NotFound e) { ... }      // never reached in production
 * </pre>
 *
 * Those catches work when the client is mocked in a unit test and never fire
 * against the real wiring, because by then the exception is no longer a
 * FeignException. The visible symptom was a brand-new patient — someone who has
 * registered but not yet created a patient profile — getting HTTP 500 with
 * "Unexpected error" from *every* screen in the application, when the truthful
 * answer was a 404 the front end already knew how to handle.
 *
 * <h2>Why here rather than in each service</h2>
 *
 * Four services make this call and four would have to remember the same
 * non-obvious unwrap. This is exactly the cross-cutting convention the starter
 * exists for (ADR-001): no business logic, one behaviour every service must
 * implement identically.
 *
 * <h2>Why it runs first</h2>
 *
 * Every service's own {@code ApiExceptionHandler} ends with a catch-all
 * {@code @ExceptionHandler(Exception.class)}. Advices are consulted in order and
 * the first with a matching method wins, so an advice registered at the same
 * precedence loses the tie and never sees anything — that catch-all matches
 * first. Hence HIGHEST_PRECEDENCE.
 *
 * That is safe precisely because this class handles exactly one narrow type: it
 * cannot shadow a service's own handlers, since none of them declare
 * NoFallbackAvailableException.
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class DownstreamFailureAdvice {

    private static final Logger log = LoggerFactory.getLogger(DownstreamFailureAdvice.class);

    @ExceptionHandler(NoFallbackAvailableException.class)
    public ProblemDetail onDownstreamFailure(NoFallbackAvailableException ex) {
        Throwable cause = ex.getCause();

        // A downstream 404 is an answer, not a fault. The most common case by far
        // is "this account has no patient profile yet", which is the normal state
        // of every user between registering and completing their profile.
        if (cause instanceof FeignException.NotFound) {
            ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.NOT_FOUND);
            problem.setType(URI.create("https://careconnect.dev/errors/profile-not-found"));
            problem.setTitle("Profile not set up");
            problem.setDetail("No profile is linked to your account yet. "
                    + "Complete your profile to use this feature.");
            return problem;
        }

        // A downstream 4xx that is not 404 means this service sent a bad request
        // on the caller's behalf. That is our defect, not theirs, so it must not
        // be reported as the caller's mistake.
        if (cause instanceof FeignException feign && feign.status() >= 400 && feign.status() < 500) {
            log.error("downstream rejected our request status={} — this is a bug in the calling service",
                    feign.status(), cause);
            ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.INTERNAL_SERVER_ERROR);
            problem.setType(URI.create("https://careconnect.dev/errors/internal"));
            problem.setTitle("Unexpected error");
            problem.setDetail("An unexpected error occurred. Quote the correlation id when reporting.");
            return problem;
        }

        // Everything else — the dependency is down, timed out, or the breaker is
        // open. 503 is what ADR-004 promises: sync calls fail fast and say so,
        // rather than degrading into a plausible-looking wrong answer.
        log.warn("downstream unavailable: {}", cause == null ? ex.getMessage() : cause.toString());
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.SERVICE_UNAVAILABLE);
        problem.setType(URI.create("https://careconnect.dev/errors/dependency-unavailable"));
        problem.setTitle("Service temporarily unavailable");
        problem.setDetail("A service this request depends on is not responding. Please try again.");
        return problem;
    }
}
