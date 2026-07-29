package com.careconnect.patient.domain;

public class ProfileAlreadyExistsException extends RuntimeException {

    public ProfileAlreadyExistsException() {
        super("A patient profile is already linked to this account");
    }
}
