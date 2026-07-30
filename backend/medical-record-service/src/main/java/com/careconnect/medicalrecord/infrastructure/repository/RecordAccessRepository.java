package com.careconnect.medicalrecord.infrastructure.repository;

import com.careconnect.medicalrecord.domain.RecordAccessEntry;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Read and append only.
 *
 * JpaRepository technically exposes delete/save-over, which is the one wart in
 * using it here; nothing in the application calls them, and the entity has no
 * setters, so a row cannot be edited through the domain. Locking that down
 * properly is a database-permissions job (a role with INSERT and SELECT but no
 * UPDATE or DELETE on this table), noted in the security doc.
 */
public interface RecordAccessRepository extends JpaRepository<RecordAccessEntry, UUID> {

    /** Who has read this patient's records — the audit and patient-facing view. */
    Page<RecordAccessEntry> findByPatientIdOrderByAccessedAtDesc(UUID patientId,
                                                                 Pageable pageable);

    /** What has this account been reading — asked when an account is suspect. */
    Page<RecordAccessEntry> findByActorUserIdOrderByAccessedAtDesc(UUID actorUserId,
                                                                   Pageable pageable);
}
