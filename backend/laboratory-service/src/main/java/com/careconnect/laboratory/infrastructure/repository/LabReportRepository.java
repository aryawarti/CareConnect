package com.careconnect.laboratory.infrastructure.repository;

import com.careconnect.laboratory.domain.LabReport;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LabReportRepository extends JpaRepository<LabReport, UUID> {
    Optional<LabReport> findByOrderId(UUID orderId);
}
