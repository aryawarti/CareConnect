package com.careconnect.provider.infrastructure.repository;

import com.careconnect.provider.domain.AvailabilitySlot;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AvailabilitySlotRepository extends JpaRepository<AvailabilitySlot, UUID> {

    List<AvailabilitySlot> findByDoctorIdOrderByDayOfWeekAscStartTimeAsc(UUID doctorId);

    /** Whole-page lookup for the directory: one query for 20 doctors' working
     *  days, rather than one per card. */
    List<AvailabilitySlot> findByDoctorIdIn(List<UUID> doctorIds);

    void deleteByDoctorId(UUID doctorId);
}
