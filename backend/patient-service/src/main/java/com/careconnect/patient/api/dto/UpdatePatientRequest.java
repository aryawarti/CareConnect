package com.careconnect.patient.api.dto;

import com.careconnect.patient.domain.Address;
import com.careconnect.patient.domain.Gender;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record UpdatePatientRequest(
        @Size(max = 80) String firstName,
        @Size(max = 80) String lastName,
        @Past LocalDate dateOfBirth,
        Gender gender,
        @Size(max = 25) String phone,
        @Email @Size(max = 255) String email,
        Address address,
        @Size(max = 160) String emergencyContactName,
        @Size(max = 25) String emergencyContactPhone) {
}
