package com.careconnect.medicalrecord.infrastructure.repository;

import com.careconnect.medicalrecord.domain.ProcessedEvent;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProcessedEventRepository extends JpaRepository<ProcessedEvent, String> {
}
