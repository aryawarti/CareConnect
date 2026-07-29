package com.careconnect.identity.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Public signup. `role` is limited to PATIENT or DOCTOR — a self-registered
 * doctor is only an *applicant* (their profile stays unverified until an
 * administrator approves it), and no one can grant themselves STAFF or ADMIN.
 */
public record RegisterRequest(
        @NotBlank @Email @Size(max = 255)
        String email,

        @NotBlank @Size(min = 10, max = 72)  // 72: BCrypt input limit
        @Pattern(regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).*$",
                 message = "must contain upper case, lower case, and a digit")
        String password,

        /** PATIENT (default) or DOCTOR. Anything else is rejected. */
        String role) {

    public String roleOrDefault() {
        return "DOCTOR".equalsIgnoreCase(role) ? "DOCTOR" : "PATIENT";
    }
}
