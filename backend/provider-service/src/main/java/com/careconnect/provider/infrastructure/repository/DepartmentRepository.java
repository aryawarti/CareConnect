package com.careconnect.provider.infrastructure.repository;

import com.careconnect.provider.domain.Department;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DepartmentRepository extends JpaRepository<Department, UUID> {
}
