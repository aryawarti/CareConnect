package com.careconnect.appointment.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Entity
@Table(name = "appointment_status_history")
@EntityListeners(AuditingEntityListener.class)
public class StatusHistoryEntry {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "appointment_id", nullable = false)
    private UUID appointmentId;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_status")
    private AppointmentStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", nullable = false)
    private AppointmentStatus toStatus;

    @CreatedBy
    @Column(name = "changed_by", updatable = false)
    private String changedBy;

    @CreatedDate
    @Column(name = "changed_at", updatable = false)
    private Instant changedAt;

    protected StatusHistoryEntry() { }

    public StatusHistoryEntry(UUID appointmentId, AppointmentStatus from, AppointmentStatus to) {
        this.appointmentId = appointmentId;
        this.fromStatus = from;
        this.toStatus = to;
    }

    public AppointmentStatus getFromStatus() { return fromStatus; }
    public AppointmentStatus getToStatus() { return toStatus; }
}
