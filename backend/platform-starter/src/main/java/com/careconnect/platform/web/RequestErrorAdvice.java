package com.careconnect.platform.web;

import java.net.URI;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Maps the mistakes a caller can make in the shape of a request to the status
 * that describes them.
 *
 * Every service's own {@code ApiExceptionHandler} ends with a catch-all
 * {@code @ExceptionHandler(Exception.class)}, and Spring MVC's own request
 * exceptions were reaching it. The result was an API that answered a missing
 * query parameter, an unknown path and a wrong HTTP method all with
 * {@code 500 Unexpected error} — telling the caller the server is broken when
 * the request was.
 *
 * That is worse than untidy. A 500 is a page for whoever is on call and a signal
 * to retry; a 400 is neither. Any bot probing for /wp-admin was manufacturing
 * server errors in the logs, and a client that omitted a parameter was told to
 * try again rather than to fix its request.
 *
 * <h2>Scope</h2>
 *
 * Deliberately narrow: only exception types that no service handles itself. In
 * particular {@code MethodArgumentNotValidException} is left alone — every
 * service maps it to a 400 carrying per-field errors, and catching it here would
 * replace those with something less useful. Verified against every
 * {@code @ExceptionHandler} in the repository before this was written.
 *
 * Runs at HIGHEST_PRECEDENCE for the same reason as
 * {@link com.careconnect.platform.client.DownstreamFailureAdvice}: at equal
 * precedence the services' catch-all wins the tie and this never runs.
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestErrorAdvice {

    private static final String ERROR_NS = "https://careconnect.dev/errors/";

    private static ProblemDetail problem(HttpStatus status, String type, String title, String detail) {
        ProblemDetail problem = ProblemDetail.forStatus(status);
        problem.setType(URI.create(ERROR_NS + type));
        problem.setTitle(title);
        problem.setDetail(detail);
        return problem;
    }

    /** A required query parameter was not sent. */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ProblemDetail onMissingParameter(MissingServletRequestParameterException ex) {
        return problem(HttpStatus.BAD_REQUEST, "missing-parameter", "Missing parameter",
                "Required parameter '" + ex.getParameterName() + "' was not supplied.");
    }

    /** A parameter was sent but could not be parsed — ?date=yesterday, ?id=abc. */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ProblemDetail onTypeMismatch(MethodArgumentTypeMismatchException ex) {
        return problem(HttpStatus.BAD_REQUEST, "invalid-parameter", "Invalid parameter",
                "Parameter '" + ex.getName() + "' is not in the expected format.");
    }

    /** Malformed or absent request body. */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ProblemDetail onUnreadableBody(HttpMessageNotReadableException ex) {
        // Deliberately not echoing the parser message: it quotes the offending
        // input back, which for a login or a clinical note means user data in a
        // response and, more importantly, in the logs.
        return problem(HttpStatus.BAD_REQUEST, "malformed-body", "Malformed request body",
                "The request body is missing or is not valid JSON.");
    }

    /** No handler for this path. */
    @ExceptionHandler(NoResourceFoundException.class)
    public ProblemDetail onNoResource(NoResourceFoundException ex) {
        return problem(HttpStatus.NOT_FOUND, "no-such-endpoint", "Not found",
                "No endpoint matches this path.");
    }

    /** Right path, wrong verb. */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ProblemDetail onMethodNotSupported(HttpRequestMethodNotSupportedException ex) {
        return problem(HttpStatus.METHOD_NOT_ALLOWED, "method-not-allowed", "Method not allowed",
                ex.getMethod() + " is not supported on this endpoint.");
    }
}
