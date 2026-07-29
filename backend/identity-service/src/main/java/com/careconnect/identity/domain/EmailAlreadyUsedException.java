package com.careconnect.identity.domain;

public class EmailAlreadyUsedException extends RuntimeException {

    public EmailAlreadyUsedException() {
        super("An account with this email already exists");
    }
}
