package com.careconnect.laboratory.infrastructure.repository;

import com.careconnect.laboratory.domain.LabResult;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LabResultRepository extends JpaRepository<LabResult, UUID> {
    List<LabResult> findByOrderItemId(UUID orderItemId);
    List<LabResult> findByOrderItemIdIn(List<UUID> orderItemIds);
}
