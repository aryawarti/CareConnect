package com.careconnect.provider.api.dto;

import com.careconnect.provider.domain.Doctor;
import com.careconnect.provider.domain.DoctorStatus;
import java.util.UUID;

/**
 * Minimal display view of a doctor, for other services that only need to label
 * something with a name — the mirror of patient-service's PatientSummary.
 *
 * Deliberately separate from {@link BookingInfo}: that computes a day's
 * availability windows, which is a lot of work to do when the caller wanted a
 * name. Deliberately separate from DoctorResponse too, which carries fees and
 * registration details and is restricted to staff.
 */
public record DoctorSummary(UUID id, String fullName, boolean active) {

    public static DoctorSummary from(Doctor doctor) {
        return new DoctorSummary(doctor.getId(),
                doctor.getFirstName() + " " + doctor.getLastName(),
                doctor.getStatus() == DoctorStatus.ACTIVE);
    }
}
