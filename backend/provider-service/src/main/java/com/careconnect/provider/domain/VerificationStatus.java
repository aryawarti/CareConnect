package com.careconnect.provider.domain;

/**
 * A doctor's standing with the hospital.
 *
 * Self-registration would be dangerous without this: anyone could sign up as a
 * cardiologist and start accepting patients. An applicant is PENDING until an
 * administrator checks their qualification and medical registration number.
 * Only APPROVED doctors appear in the patient-facing directory.
 */
public enum VerificationStatus {
    PENDING, APPROVED, REJECTED
}
