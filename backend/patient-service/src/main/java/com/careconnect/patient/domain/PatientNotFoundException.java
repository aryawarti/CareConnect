package com.careconnect.patient.domain;

import java.util.UUID;

public class PatientNotFoundException extends RuntimeException {

    public PatientNotFoundException(UUID id) {
        super("Patient %s not found".formatted(id));
    }

    public PatientNotFoundException(String message) {
        super(message);
    }
}
