package com.careconnect.provider.infrastructure.repository;

import com.careconnect.provider.domain.Doctor;
import com.careconnect.provider.domain.DoctorStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DoctorRepository extends JpaRepository<Doctor, UUID> {

    @EntityGraph(attributePaths = "department")
    Optional<Doctor> findByUserId(UUID userId);

    /** Overrides the inherited lookup so every findById() caller gets department
     *  eagerly, not just the ones that happened to remember @EntityGraph. */
    @EntityGraph(attributePaths = "department")
    Optional<Doctor> findById(UUID id);

    /** Directory: active doctors, optional specialty/name filter. EntityGraph avoids n+1 on department. */
    @EntityGraph(attributePaths = "department")
    @Query("""
            select d from Doctor d
            where d.status = :status
              and d.verification = com.careconnect.provider.domain.VerificationStatus.APPROVED
              and (:q is null or :q = ''
                   or lower(d.specialty) like lower(concat('%', :q, '%'))
                   or lower(d.lastName)  like lower(concat('%', :q, '%'))
                   or lower(d.firstName) like lower(concat('%', :q, '%')))
            """)
    Page<Doctor> directory(@Param("q") String query, @Param("status") DoctorStatus status,
                           Pageable pageable);

    /** Applications waiting for an administrator to verify credentials. */
    @EntityGraph(attributePaths = "department")
    List<Doctor> findByVerificationOrderByIdAsc(
            com.careconnect.provider.domain.VerificationStatus verification);

    @EntityGraph(attributePaths = "department")
    Page<Doctor> findAllByOrderByLastNameAsc(Pageable pageable);
}
