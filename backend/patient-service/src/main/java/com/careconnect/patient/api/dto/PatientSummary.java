package com.careconnect.patient.api.dto;

import com.careconnect.patient.domain.Patient;
import com.careconnect.patient.domain.PatientStatus;
import java.util.UUID;

/**
 * Minimal cross-service view (consumed by appointment-service via Feign).
 * Deliberately tiny: existence, activity, display name — nothing clinical.
 */
public record PatientSummary(UUID id, boolean active, String fullName) {

    public static PatientSummary from(Patient p) {
        return new PatientSummary(p.getId(), p.getStatus() == PatientStatus.ACTIVE,
                p.getFirstName() + " " + p.getLastName());
    }
}
