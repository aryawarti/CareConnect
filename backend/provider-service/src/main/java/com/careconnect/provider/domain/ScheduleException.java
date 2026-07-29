package com.careconnect.provider.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.UUID;

/** A day the doctor is NOT available despite the weekly schedule (leave, conference). */
@Entity
@Table(name = "schedule_exceptions")
public class ScheduleException {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "doctor_id", nullable = false)
    private UUID doctorId;

    @Column(name = "exception_date", nullable = false)
    private LocalDate exceptionDate;

    private String reason;

    protected ScheduleException() { }

    public ScheduleException(UUID doctorId, LocalDate exceptionDate, String reason) {
        this.doctorId = doctorId;
        this.exceptionDate = exceptionDate;
        this.reason = reason;
    }

    public UUID getId() { return id; }
    public UUID getDoctorId() { return doctorId; }
    public LocalDate getExceptionDate() { return exceptionDate; }
    public String getReason() { return reason; }
}
