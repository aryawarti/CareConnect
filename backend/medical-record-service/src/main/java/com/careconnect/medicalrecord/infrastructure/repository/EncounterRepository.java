package com.careconnect.medicalrecord.infrastructure.repository;

import com.careconnect.medicalrecord.domain.Encounter;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EncounterRepository extends JpaRepository<Encounter, UUID> {

    boolean existsByAppointmentId(UUID appointmentId);

    Optional<Encounter> findByAppointmentId(UUID appointmentId);

    // No @EntityGraph here: Hibernate can't eager-fetch two List-typed
    // collections (diagnoses + prescriptions) in one query
    // (MultipleBagFetchException). Callers only need EncounterResponse.summary()
    // from this list, which doesn't touch either collection.
    Page<Encounter> findByPatientIdOrderByOccurredAtDesc(UUID patientId, Pageable pageable);

    Page<Encounter> findByDoctorIdOrderByOccurredAtDesc(UUID doctorId, Pageable pageable);
}
