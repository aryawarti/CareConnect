package com.careconnect.medicalrecord.infrastructure.repository;

import com.careconnect.medicalrecord.domain.EncounterLabReport;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EncounterLabReportRepository extends JpaRepository<EncounterLabReport, UUID> {
    boolean existsByOrderId(UUID orderId);
    List<EncounterLabReport> findByEncounterId(UUID encounterId);
    List<EncounterLabReport> findByPatientId(UUID patientId);
}
