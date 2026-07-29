package com.careconnect.appointment.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * Aggregate root of the Scheduling context. All state changes go through the
 * transition methods — the status field is never set directly, so the state
 * machine in {@link AppointmentStatus} is the single source of legality.
 */
@Entity
@Table(name = "appointments")
@EntityListeners(AuditingEntityListener.class)
public class Appointment {

    /** FR-D3: patients may cancel up to this long before the start. */
    public static final Duration PATIENT_CANCEL_CUTOFF = Duration.ofHours(2);

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "patient_id", nullable = false, updatable = false)
    private UUID patientId;

    @Column(name = "doctor_id", nullable = false, updatable = false)
    private UUID doctorId;

    @Column(name = "start_at", nullable = false, updatable = false)
    private Instant startAt;

    @Column(name = "end_at", nullable = false, updatable = false)
    private Instant endAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AppointmentStatus status = AppointmentStatus.REQUESTED;

    @Column(length = 500)
    private String reason;

    @Column(name = "fee_snapshot", nullable = false, updatable = false)
    private BigDecimal feeSnapshot;

    @Column(name = "patient_name", nullable = false, updatable = false)
    private String patientName;

    @Column(name = "doctor_name", nullable = false, updatable = false)
    private String doctorName;

    @CreatedDate  @Column(name = "created_at", updatable = false) private Instant createdAt;
    @LastModifiedDate @Column(name = "updated_at") private Instant updatedAt;
    @CreatedBy    @Column(name = "created_by", updatable = false) private String createdBy;
    @LastModifiedBy @Column(name = "updated_by") private String updatedBy;

    protected Appointment() { }

    public Appointment(UUID patientId, UUID doctorId, Instant startAt, Instant endAt,
                       String reason, BigDecimal feeSnapshot,
                       String patientName, String doctorName) {
        this.patientId = patientId;
        this.doctorId = doctorId;
        this.startAt = startAt;
        this.endAt = endAt;
        this.reason = reason;
        this.feeSnapshot = feeSnapshot;
        this.patientName = patientName;
        this.doctorName = doctorName;
    }

    public void confirm()  { transition(AppointmentStatus.CONFIRMED); }
    public void complete() { transition(AppointmentStatus.COMPLETED); }
    public void markNoShow() { transition(AppointmentStatus.NO_SHOW); }

    /** Staff cancel: any time before completion. */
    public void cancel() { transition(AppointmentStatus.CANCELLED); }

    /** Patient cancel: enforces the cutoff rule (FR-D3). */
    public void cancelAsPatient(Instant now) {
        if (now.plus(PATIENT_CANCEL_CUTOFF).isAfter(startAt)) {
            throw new InvalidTransitionException(
                    "Appointments can no longer be cancelled within %d hours of the start"
                            .formatted(PATIENT_CANCEL_CUTOFF.toHours()));
        }
        transition(AppointmentStatus.CANCELLED);
    }

    public boolean isOwnedByPatientUser(UUID patientId) {
        return this.patientId.equals(patientId);
    }

    private void transition(AppointmentStatus target) {
        if (!status.canTransitionTo(target)) {
            throw new InvalidTransitionException(
                    "Cannot move appointment from %s to %s".formatted(status, target));
        }
        this.status = target;
    }

    public UUID getId() { return id; }
    public UUID getPatientId() { return patientId; }
    public UUID getDoctorId() { return doctorId; }
    public Instant getStartAt() { return startAt; }
    public Instant getEndAt() { return endAt; }
    public AppointmentStatus getStatus() { return status; }
    public String getReason() { return reason; }
    public BigDecimal getFeeSnapshot() { return feeSnapshot; }
    public String getPatientName() { return patientName; }
    public String getDoctorName() { return doctorName; }
}
