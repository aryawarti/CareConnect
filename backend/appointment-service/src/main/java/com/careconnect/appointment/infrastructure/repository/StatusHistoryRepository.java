package com.careconnect.appointment.infrastructure.repository;

import com.careconnect.appointment.domain.StatusHistoryEntry;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StatusHistoryRepository extends JpaRepository<StatusHistoryEntry, UUID> {

    List<StatusHistoryEntry> findByAppointmentIdOrderByChangedAtAsc(UUID appointmentId);
}
