package com.careconnect.identity.domain;

/**
 * Domain-level authentication failures. Message is safe for clients:
 * deliberately vague on *why* login failed (no user-enumeration oracle).
 */
public class AuthException extends RuntimeException {

    public AuthException(String message) {
        super(message);
    }
}
