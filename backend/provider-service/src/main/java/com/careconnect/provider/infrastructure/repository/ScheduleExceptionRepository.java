package com.careconnect.provider.infrastructure.repository;

import com.careconnect.provider.domain.ScheduleException;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ScheduleExceptionRepository extends JpaRepository<ScheduleException, UUID> {

    List<ScheduleException> findByDoctorIdAndExceptionDateGreaterThanEqualOrderByExceptionDate(
            UUID doctorId, LocalDate from);
}
