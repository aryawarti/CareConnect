package com.careconnect.queue.infrastructure.repository;

import com.careconnect.queue.domain.QueueEntry;
import com.careconnect.queue.domain.QueueStatus;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface QueueEntryRepository extends JpaRepository<QueueEntry, UUID> {

    Optional<QueueEntry> findByAppointmentId(UUID appointmentId);

    boolean existsByAppointmentId(UUID appointmentId);

    /**
     * The live queue, in calling order: priority group first, then arrival.
     * This ordering IS the fairness policy, so it lives in one query rather
     * than being re-implemented by each caller.
     */
    @Query("""
            select q from QueueEntry q
            where q.doctorId = :doctorId and q.queueDate = :date
              and q.status in (com.careconnect.queue.domain.QueueStatus.WAITING,
                               com.careconnect.queue.domain.QueueStatus.CALLED,
                               com.careconnect.queue.domain.QueueStatus.IN_CONSULTATION)
            order by q.priority asc, q.checkedInAt asc
            """)
    List<QueueEntry> liveQueue(@Param("doctorId") UUID doctorId, @Param("date") LocalDate date);

    List<QueueEntry> findByDoctorIdAndQueueDateOrderByCheckedInAtAsc(UUID doctorId, LocalDate date);

    List<QueueEntry> findByPatientIdAndQueueDateOrderByCheckedInAtDesc(UUID patientId, LocalDate date);

    /** Recent consultation durations for the wait-time model. */
    @Query("""
            select q.consultationSeconds from QueueEntry q
            where q.doctorId = :doctorId and q.consultationSeconds is not null
            order by q.completedAt desc
            limit 20
            """)
    List<Integer> recentConsultationSeconds(@Param("doctorId") UUID doctorId);

    long countByQueueDateAndStatus(LocalDate date, QueueStatus status);

    List<QueueEntry> findByQueueDateAndStatusIn(LocalDate date, List<QueueStatus> statuses);
}
