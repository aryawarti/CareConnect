package com.careconnect.appointment.domain;

import java.util.UUID;

public class AppointmentNotFoundException extends RuntimeException {

    public AppointmentNotFoundException(UUID id) {
        super("Appointment %s not found".formatted(id));
    }
}
