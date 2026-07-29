package com.careconnect.appointment.infrastructure.repository;

import com.careconnect.appointment.domain.ProcessedEvent;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProcessedEventRepository extends JpaRepository<ProcessedEvent, String> {
}
