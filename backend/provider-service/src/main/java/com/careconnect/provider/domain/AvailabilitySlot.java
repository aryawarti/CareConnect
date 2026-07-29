package com.careconnect.provider.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalTime;
import java.util.UUID;

/** A weekly recurring availability window (e.g. Mon 09:00–13:00, 30-min slots). */
@Entity
@Table(name = "availability_slots")
public class AvailabilitySlot {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "doctor_id", nullable = false)
    private UUID doctorId;

    /** ISO day-of-week: 1 = Monday … 7 = Sunday (matches java.time.DayOfWeek). */
    @Column(name = "day_of_week", nullable = false)
    private int dayOfWeek;

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;

    @Column(name = "slot_minutes", nullable = false)
    private int slotMinutes = 30;

    protected AvailabilitySlot() { }

    public AvailabilitySlot(UUID doctorId, int dayOfWeek, LocalTime startTime,
                            LocalTime endTime, int slotMinutes) {
        this.doctorId = doctorId;
        this.dayOfWeek = dayOfWeek;
        this.startTime = startTime;
        this.endTime = endTime;
        this.slotMinutes = slotMinutes;
    }

    public boolean overlaps(AvailabilitySlot other) {
        return this.dayOfWeek == other.dayOfWeek
                && this.startTime.isBefore(other.endTime)
                && other.startTime.isBefore(this.endTime);
    }

    public UUID getId() { return id; }
    public UUID getDoctorId() { return doctorId; }
    public int getDayOfWeek() { return dayOfWeek; }
    public LocalTime getStartTime() { return startTime; }
    public LocalTime getEndTime() { return endTime; }
    public int getSlotMinutes() { return slotMinutes; }
}
