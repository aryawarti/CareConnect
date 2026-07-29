package com.careconnect.provider.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Entity
@Table(name = "doctors")
@EntityListeners(AuditingEntityListener.class)
public class Doctor {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "user_id", unique = true)
    private UUID userId;

    @Column(name = "first_name", nullable = false)
    private String firstName;

    @Column(name = "last_name", nullable = false)
    private String lastName;

    @Column(nullable = false)
    private String specialty;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "department_id")
    private Department department;

    @Column(name = "consultation_fee", nullable = false)
    private BigDecimal consultationFee;

    private String email;
    private String phone;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DoctorStatus status = DoctorStatus.ACTIVE;

    /** Hospital's verification of this doctor's credentials. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private VerificationStatus verification = VerificationStatus.APPROVED;

    private String qualification;

    @Column(name = "registration_no")
    private String registrationNo;

    @Column(name = "experience_years")
    private Short experienceYears;

    @Column(length = 600)
    private String bio;

    @Column(name = "rejection_reason")
    private String rejectionReason;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @CreatedDate  @Column(name = "created_at", updatable = false) private Instant createdAt;
    @LastModifiedDate @Column(name = "updated_at") private Instant updatedAt;
    @CreatedBy    @Column(name = "created_by", updatable = false) private String createdBy;
    @LastModifiedBy @Column(name = "updated_by") private String updatedBy;

    protected Doctor() { }

    public Doctor(String firstName, String lastName, String specialty,
                  Department department, BigDecimal consultationFee) {
        this.firstName = firstName;
        this.lastName = lastName;
        this.specialty = specialty;
        this.department = department;
        this.consultationFee = consultationFee;
    }

    /** Applications start unverified and stay invisible to patients. */
    public void submitForVerification() {
        this.verification = VerificationStatus.PENDING;
    }

    public void approve() {
        this.verification = VerificationStatus.APPROVED;
        this.rejectionReason = null;
        this.reviewedAt = Instant.now();
    }

    public void reject(String reason) {
        this.verification = VerificationStatus.REJECTED;
        this.rejectionReason = reason;
        this.reviewedAt = Instant.now();
    }

    public boolean isBookable() {
        return status == DoctorStatus.ACTIVE && verification == VerificationStatus.APPROVED;
    }

    public void updateCredentials(String qualification, String registrationNo,
                                  Short experienceYears, String bio) {
        this.qualification = qualification;
        this.registrationNo = registrationNo;
        this.experienceYears = experienceYears;
        this.bio = bio;
    }

    public boolean isOwnedBy(UUID userId) {
        return this.userId != null && this.userId.equals(userId);
    }

    public void deactivate() { this.status = DoctorStatus.INACTIVE; }
    public void linkUser(UUID userId) { this.userId = userId; }

    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public String getFirstName() { return firstName; }
    public String getLastName() { return lastName; }
    public String getSpecialty() { return specialty; }
    public Department getDepartment() { return department; }
    public BigDecimal getConsultationFee() { return consultationFee; }
    public String getEmail() { return email; }
    public String getPhone() { return phone; }
    public DoctorStatus getStatus() { return status; }
    public VerificationStatus getVerification() { return verification; }
    public String getQualification() { return qualification; }
    public String getRegistrationNo() { return registrationNo; }
    public Short getExperienceYears() { return experienceYears; }
    public String getBio() { return bio; }
    public String getRejectionReason() { return rejectionReason; }
    public Instant getReviewedAt() { return reviewedAt; }

    public void setFirstName(String v) { this.firstName = v; }
    public void setLastName(String v) { this.lastName = v; }
    public void setSpecialty(String v) { this.specialty = v; }
    public void setDepartment(Department v) { this.department = v; }
    public void setConsultationFee(BigDecimal v) { this.consultationFee = v; }
    public void setEmail(String v) { this.email = v; }
    public void setPhone(String v) { this.phone = v; }
}
