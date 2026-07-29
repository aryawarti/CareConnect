package com.careconnect.laboratory.infrastructure.repository;

import com.careconnect.laboratory.domain.TestAnalyte;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TestAnalyteRepository extends JpaRepository<TestAnalyte, UUID> {
    List<TestAnalyte> findByTestIdOrderByDisplayOrderAsc(UUID testId);
}
