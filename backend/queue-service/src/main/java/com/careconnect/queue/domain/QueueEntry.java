package com.careconnect.queue.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/** One patient's place in one doctor's queue on one day. */
@Entity
@Table(name = "queue_entries")
@EntityListeners(AuditingEntityListener.class)
public class QueueEntry {

    /** Called this many times without appearing → skipped, queue moves on. */
    public static final int MAX_CALL_ATTEMPTS = 3;

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "appointment_id", unique = true, updatable = false)
    private UUID appointmentId;

    @Column(name = "patient_id", nullable = false, updatable = false)
    private UUID patientId;

    @Column(name = "doctor_id", nullable = false, updatable = false)
    private UUID doctorId;

    @Column(name = "patient_name", nullable = false, updatable = false)
    private String patientName;

    @Column(name = "doctor_name", nullable = false, updatable = false)
    private String doctorName;

    @Column(name = "token_number", nullable = false, updatable = false)
    private String tokenNumber;

    @Column(name = "queue_date", nullable = false, updatable = false)
    private LocalDate queueDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private QueuePriority priority = QueuePriority.NORMAL;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private QueueStatus status = QueueStatus.WAITING;

    private String complaint;

    @Column(name = "checked_in_at", nullable = false, updatable = false)
    private Instant checkedInAt = Instant.now();

    @Column(name = "called_at")
    private Instant calledAt;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    /** Actual time with the doctor — the raw material of the wait-time model. */
    @Column(name = "consultation_secs")
    private Integer consultationSeconds;

    @Column(name = "call_attempts", nullable = false)
    private short callAttempts;

    @CreatedDate  @Column(name = "created_at", updatable = false) private Instant createdAt;
    @LastModifiedDate @Column(name = "updated_at") private Instant updatedAt;
    @CreatedBy    @Column(name = "created_by", updatable = false) private String createdBy;
    @LastModifiedBy @Column(name = "updated_by") private String updatedBy;

    protected QueueEntry() { }

    public QueueEntry(UUID appointmentId, UUID patientId, UUID doctorId, String patientName,
                      String doctorName, String tokenNumber, LocalDate queueDate,
                      QueuePriority priority, String complaint) {
        this.appointmentId = appointmentId;
        this.patientId = patientId;
        this.doctorId = doctorId;
        this.patientName = patientName;
        this.doctorName = doctorName;
        this.tokenNumber = tokenNumber;
        this.queueDate = queueDate;
        this.priority = priority == null ? QueuePriority.NORMAL : priority;
        this.complaint = complaint;
    }

    public void call() {
        requireStatus(QueueStatus.WAITING, QueueStatus.CALLED);
        this.status = QueueStatus.CALLED;
        this.calledAt = Instant.now();
        this.callAttempts++;
    }

    public void startConsultation() {
        requireStatus(QueueStatus.WAITING, QueueStatus.CALLED);
        this.status = QueueStatus.IN_CONSULTATION;
        this.startedAt = Instant.now();
    }

    public void completeConsultation() {
        requireStatus(QueueStatus.IN_CONSULTATION);
        this.status = QueueStatus.COMPLETED;
        this.completedAt = Instant.now();
        Instant from = startedAt == null ? checkedInAt : startedAt;
        this.consultationSeconds = (int) Duration.between(from, completedAt).toSeconds();
    }

    /** Called too many times without appearing. */
    public void skip() {
        requireStatus(QueueStatus.WAITING, QueueStatus.CALLED);
        this.status = QueueStatus.SKIPPED;
    }

    /** Patient gave up and left — a quality signal, not a silent delete. */
    public void markLeft() {
        requireStatus(QueueStatus.WAITING, QueueStatus.CALLED);
        this.status = QueueStatus.LEFT;
    }

    /** A skipped patient who turns up later goes back in, at the current time. */
    public void requeue() {
        requireStatus(QueueStatus.SKIPPED);
        this.status = QueueStatus.WAITING;
        this.checkedInAt = Instant.now();
        this.callAttempts = 0;
    }

    public boolean exhaustedCallAttempts() {
        return callAttempts >= MAX_CALL_ATTEMPTS;
    }

    public long waitedMinutes() {
        Instant end = startedAt != null ? startedAt : Instant.now();
        return Duration.between(checkedInAt, end).toMinutes();
    }

    private void requireStatus(QueueStatus... allowed) {
        for (QueueStatus candidate : allowed) {
            if (status == candidate) {
                return;
            }
        }
        throw new InvalidQueueTransitionException(
                "Cannot do that while the token is %s".formatted(status));
    }

    public UUID getId() { return id; }
    public UUID getAppointmentId() { return appointmentId; }
    public UUID getPatientId() { return patientId; }
    public UUID getDoctorId() { return doctorId; }
    public String getPatientName() { return patientName; }
    public String getDoctorName() { return doctorName; }
    public String getTokenNumber() { return tokenNumber; }
    public LocalDate getQueueDate() { return queueDate; }
    public QueuePriority getPriority() { return priority; }
    public QueueStatus getStatus() { return status; }
    public String getComplaint() { return complaint; }
    public Instant getCheckedInAt() { return checkedInAt; }
    public Instant getCalledAt() { return calledAt; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getCompletedAt() { return completedAt; }
    public Integer getConsultationSeconds() { return consultationSeconds; }
    public short getCallAttempts() { return callAttempts; }
}
