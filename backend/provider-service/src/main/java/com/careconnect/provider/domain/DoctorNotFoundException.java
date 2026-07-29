package com.careconnect.provider.domain;

import java.util.UUID;

public class DoctorNotFoundException extends RuntimeException {

    public DoctorNotFoundException(UUID id) {
        super("Doctor %s not found".formatted(id));
    }

    public DoctorNotFoundException(String message) {
        super(message);
    }
}
