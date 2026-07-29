package com.careconnect.appointment.domain;

/**
 * A synchronous dependency (patient/provider) is down or the circuit is open.
 * Booking fails fast with 503 — we do not book unverifiable appointments
 * (ADR-004; the asymmetry with async consumers is deliberate, NFR-2).
 */
public class DependencyUnavailableException extends RuntimeException {

    public DependencyUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
