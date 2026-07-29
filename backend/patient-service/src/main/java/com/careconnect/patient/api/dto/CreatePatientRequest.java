package com.careconnect.patient.api.dto;

import com.careconnect.patient.domain.Address;
import com.careconnect.patient.domain.Gender;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.UUID;

public record CreatePatientRequest(
        @NotBlank @Size(max = 80) String firstName,
        @NotBlank @Size(max = 80) String lastName,
        @NotNull @Past LocalDate dateOfBirth,
        @NotNull Gender gender,
        @Size(max = 25) String phone,
        @Email @Size(max = 255) String email,
        Address address,
        @Size(max = 160) String emergencyContactName,
        @Size(max = 25) String emergencyContactPhone,
        UUID userId) {

    public UpdatePatientRequest toUpdate() {
        return new UpdatePatientRequest(firstName, lastName, dateOfBirth, gender,
                phone, email, address, emergencyContactName, emergencyContactPhone);
    }
}
