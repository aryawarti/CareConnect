package com.careconnect.patient.api.dto;

import com.careconnect.patient.domain.Address;
import com.careconnect.patient.domain.Gender;
import com.careconnect.patient.domain.Patient;
import com.careconnect.patient.domain.PatientStatus;
import java.time.LocalDate;
import java.util.UUID;

public record PatientResponse(
        UUID id,
        String patientNumber,
        String firstName,
        String lastName,
        LocalDate dateOfBirth,
        Gender gender,
        String phone,
        String email,
        Address address,
        String emergencyContactName,
        String emergencyContactPhone,
        PatientStatus status) {

    public static PatientResponse from(Patient p) {
        return new PatientResponse(p.getId(), p.getPatientNumber(), p.getFirstName(),
                p.getLastName(), p.getDateOfBirth(), p.getGender(), p.getPhone(),
                p.getEmail(), p.getAddress(), p.getEmergencyContactName(),
                p.getEmergencyContactPhone(), p.getStatus());
    }
}
