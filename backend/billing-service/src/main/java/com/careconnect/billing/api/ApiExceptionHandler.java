package com.careconnect.billing.api;

import com.careconnect.billing.domain.InvoiceNotFoundException;
import java.net.URI;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** RFC 7807 errors — same contract as every CareConnect service. */
@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);
    private static final String ERROR_NS = "https://careconnect.dev/errors/";

    record FieldError(String field, String message) { }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail onValidation(MethodArgumentNotValidException ex) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        problem.setType(URI.create(ERROR_NS + "validation"));
        problem.setTitle("Validation failed");
        List<FieldError> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(f -> new FieldError(f.getField(), f.getDefaultMessage()))
                .toList();
        problem.setProperty("errors", errors);
        return problem;
    }

    @ExceptionHandler(InvoiceNotFoundException.class)
    ProblemDetail onNotFound(InvoiceNotFoundException ex) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.NOT_FOUND);
        problem.setType(URI.create(ERROR_NS + "invoice-not-found"));
        problem.setTitle("Invoice not found");
        problem.setDetail(ex.getMessage());
        return problem;
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ProblemDetail onIllegalArgument(IllegalArgumentException ex) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        problem.setType(URI.create(ERROR_NS + "invalid-request"));
        problem.setTitle("Invalid request");
        problem.setDetail(ex.getMessage());
        return problem;
    }

    @ExceptionHandler(com.careconnect.billing.domain.InvoiceStateException.class)
    ProblemDetail onInvoiceState(com.careconnect.billing.domain.InvoiceStateException ex) {
        // e.g. paying twice, voiding a paid invoice, partial payment
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.CONFLICT);
        problem.setType(URI.create(ERROR_NS + "invoice-state"));
        problem.setTitle("Invoice state conflict");
        problem.setDetail(ex.getMessage());
        return problem;
    }

    @ExceptionHandler(AccessDeniedException.class)   // covers AuthorizationDeniedException too
    ProblemDetail onDenied(AccessDeniedException ex) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.FORBIDDEN);
        problem.setType(URI.create(ERROR_NS + "forbidden"));
        problem.setTitle("Access denied");
        problem.setDetail("Your role does not permit this operation");
        return problem;
    }

    @ExceptionHandler(org.springframework.dao.DataIntegrityViolationException.class)
    ProblemDetail onDuplicatePayment(org.springframework.dao.DataIntegrityViolationException ex) {
        // Unique payment reference violated: a retried/duplicated payment submit.
        log.warn("duplicate payment reference rejected");
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.CONFLICT);
        problem.setType(URI.create(ERROR_NS + "duplicate-payment"));
        problem.setTitle("Duplicate payment");
        problem.setDetail("This payment was already submitted");
        return problem;
    }

    @ExceptionHandler(Exception.class)
    ProblemDetail onUnexpected(Exception ex) {
        log.error("unhandled exception", ex);
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.INTERNAL_SERVER_ERROR);
        problem.setType(URI.create(ERROR_NS + "internal"));
        problem.setTitle("Unexpected error");
        problem.setDetail("An unexpected error occurred. Quote the correlation id when reporting.");
        return problem;
    }
}
