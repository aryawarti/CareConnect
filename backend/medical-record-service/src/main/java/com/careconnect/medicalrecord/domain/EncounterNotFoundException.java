package com.careconnect.medicalrecord.domain;

import java.util.UUID;

public class EncounterNotFoundException extends RuntimeException {

    public EncounterNotFoundException(UUID id) {
        super("Encounter %s not found".formatted(id));
    }
}
