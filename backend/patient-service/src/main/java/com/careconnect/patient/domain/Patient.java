package com.careconnect.patient.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Entity
@Table(name = "patients")
@EntityListeners(AuditingEntityListener.class)
public class Patient {

    @Id
    @GeneratedValue
    private UUID id;

    /** identity-service userId — cross-context reference by UUID only (ADR-003). */
    @Column(name = "user_id", unique = true)
    private UUID userId;

    @Column(name = "patient_number", nullable = false, unique = true, updatable = false)
    private String patientNumber;

    @Column(name = "first_name", nullable = false)
    private String firstName;

    @Column(name = "last_name", nullable = false)
    private String lastName;

    @Column(name = "date_of_birth", nullable = false)
    private LocalDate dateOfBirth;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Gender gender;

    private String phone;
    private String email;

    @Embedded
    private Address address;

    @Column(name = "emergency_contact_name")
    private String emergencyContactName;

    @Column(name = "emergency_contact_phone")
    private String emergencyContactPhone;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PatientStatus status = PatientStatus.ACTIVE;

    @CreatedDate  @Column(name = "created_at", updatable = false) private Instant createdAt;
    @LastModifiedDate @Column(name = "updated_at") private Instant updatedAt;
    @CreatedBy    @Column(name = "created_by", updatable = false) private String createdBy;
    @LastModifiedBy @Column(name = "updated_by") private String updatedBy;

    protected Patient() { }

    public Patient(String patientNumber, String firstName, String lastName,
                   LocalDate dateOfBirth, Gender gender) {
        this.patientNumber = patientNumber;
        this.firstName = firstName;
        this.lastName = lastName;
        this.dateOfBirth = dateOfBirth;
        this.gender = gender;
    }

    /** Soft-deactivate only — medical-adjacent data is never hard-deleted (FR-B3). */
    public void deactivate() {
        this.status = PatientStatus.INACTIVE;
    }

    public void reactivate() {
        this.status = PatientStatus.ACTIVE;
    }

    public boolean isOwnedBy(UUID userId) {
        return this.userId != null && this.userId.equals(userId);
    }

    public void linkUser(UUID userId) {
        this.userId = userId;
    }

    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public String getPatientNumber() { return patientNumber; }
    public String getFirstName() { return firstName; }
    public String getLastName() { return lastName; }
    public LocalDate getDateOfBirth() { return dateOfBirth; }
    public Gender getGender() { return gender; }
    public String getPhone() { return phone; }
    public String getEmail() { return email; }
    public Address getAddress() { return address; }
    public String getEmergencyContactName() { return emergencyContactName; }
    public String getEmergencyContactPhone() { return emergencyContactPhone; }
    public PatientStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void setFirstName(String v) { this.firstName = v; }
    public void setLastName(String v) { this.lastName = v; }
    public void setDateOfBirth(LocalDate v) { this.dateOfBirth = v; }
    public void setGender(Gender v) { this.gender = v; }
    public void setPhone(String v) { this.phone = v; }
    public void setEmail(String v) { this.email = v; }
    public void setAddress(Address v) { this.address = v; }
    public void setEmergencyContactName(String v) { this.emergencyContactName = v; }
    public void setEmergencyContactPhone(String v) { this.emergencyContactPhone = v; }
}
