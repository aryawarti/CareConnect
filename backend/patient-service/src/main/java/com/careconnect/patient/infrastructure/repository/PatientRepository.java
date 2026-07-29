package com.careconnect.patient.infrastructure.repository;

import com.careconnect.patient.domain.Patient;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PatientRepository extends JpaRepository<Patient, UUID> {

    Optional<Patient> findByUserId(UUID userId);

    boolean existsByUserId(UUID userId);

    /**
     * One search endpoint, one query (FR-B2): name, phone, or MRN.
     * Case-insensitive; the lower(last_name) index covers the common case.
     */
    @Query("""
            select p from Patient p
            where lower(p.firstName) like lower(concat('%', :q, '%'))
               or lower(p.lastName)  like lower(concat('%', :q, '%'))
               or p.phone            like concat('%', :q, '%')
               or lower(p.patientNumber) = lower(:q)
            """)
    Page<Patient> search(@Param("q") String query, Pageable pageable);

    @Query(value = "select nextval('patient_number_seq')", nativeQuery = true)
    long nextPatientNumber();
}
