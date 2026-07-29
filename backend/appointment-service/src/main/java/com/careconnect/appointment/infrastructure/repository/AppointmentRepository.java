package com.careconnect.appointment.infrastructure.repository;

import com.careconnect.appointment.domain.Appointment;
import com.careconnect.appointment.domain.AppointmentStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AppointmentRepository extends JpaRepository<Appointment, UUID> {

    Page<Appointment> findByPatientIdOrderByStartAtDesc(UUID patientId, Pageable pageable);

    @Query("""
            select a from Appointment a
            where a.doctorId = :doctorId
              and a.startAt >= :from and a.startAt < :to
            order by a.startAt
            """)
    List<Appointment> doctorDay(@Param("doctorId") UUID doctorId,
                                @Param("from") Instant from, @Param("to") Instant to);

    /** Requests awaiting this doctor's decision. */
    List<Appointment> findByDoctorIdAndStatusOrderByStartAtAsc(UUID doctorId, AppointmentStatus status);

    /** Clinic-wide day view for the staff dashboard. */
    List<Appointment> findByStartAtBetweenOrderByStartAt(Instant from, Instant to);

    /** Slots still held (REQUESTED/CONFIRMED) for availability computation. */
    @Query("""
            select a from Appointment a
            where a.doctorId = :doctorId
              and a.startAt >= :from and a.startAt < :to
              and a.status in (com.careconnect.appointment.domain.AppointmentStatus.REQUESTED,
                               com.careconnect.appointment.domain.AppointmentStatus.CONFIRMED)
            """)
    List<Appointment> blockingAppointments(@Param("doctorId") UUID doctorId,
                                           @Param("from") Instant from, @Param("to") Instant to);
}
