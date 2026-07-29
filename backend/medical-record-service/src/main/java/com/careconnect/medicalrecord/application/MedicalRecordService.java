package com.careconnect.medicalrecord.application;

import com.careconnect.medicalrecord.api.dto.RecordDtos.AddDiagnosisRequest;
import com.careconnect.medicalrecord.api.dto.RecordDtos.AddPrescriptionRequest;
import com.careconnect.medicalrecord.api.dto.RecordDtos.AmendRequest;
import com.careconnect.medicalrecord.api.dto.RecordDtos.UpdateEncounterRequest;
import com.careconnect.medicalrecord.domain.Encounter;
import com.careconnect.medicalrecord.domain.EncounterNotFoundException;
import com.careconnect.medicalrecord.infrastructure.repository.EncounterRepository;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MedicalRecordService {

    private static final Logger log = LoggerFactory.getLogger(MedicalRecordService.class);

    private final EncounterRepository encounters;

    public MedicalRecordService(EncounterRepository encounters) {
        this.encounters = encounters;
    }

    /**
     * Called by the event consumer only — encounters exist because a visit
     * happened, not because someone posted JSON. Idempotent on appointmentId.
     */
    @Transactional
    public Encounter openFromCompletedAppointment(UUID appointmentId, UUID patientId, UUID doctorId,
                                                  String patientName, String doctorName,
                                                  Instant occurredAt) {
        return encounters.findByAppointmentId(appointmentId).orElseGet(() -> {
            Encounter encounter = encounters.save(new Encounter(appointmentId, patientId,
                    doctorId, patientName, doctorName, occurredAt));
            log.info("encounter opened id={} appointment={}", encounter.getId(), appointmentId);
            return encounter;
        });
    }

    @Transactional(readOnly = true)
    public Encounter get(UUID id) {
        return encounters.findById(id).orElseThrow(() -> new EncounterNotFoundException(id));
    }

    /**
     * Access rule (FR-E3): the treating doctor writes; the patient reads their
     * own; staff/admin read for administrative purposes. Anyone else is denied
     * even with a valid token — role alone is never sufficient for clinical data.
     */
    @Transactional(readOnly = true)
    public Encounter getForReader(UUID id, UUID callerId, boolean isStaff, boolean isPatientRole) {
        Encounter encounter = get(id);
        boolean permitted = isStaff
                || encounter.isTreatingDoctor(callerId)
                || (isPatientRole && encounter.belongsToPatient(callerId));
        if (!permitted) {
            throw new AccessDeniedException("You do not have access to this record");
        }
        return encounter;
    }

    @Transactional(readOnly = true)
    public Page<Encounter> forPatient(UUID patientId, Pageable pageable) {
        return encounters.findByPatientIdOrderByOccurredAtDesc(patientId, pageable);
    }

    @Transactional(readOnly = true)
    public Page<Encounter> forDoctor(UUID doctorId, Pageable pageable) {
        return encounters.findByDoctorIdOrderByOccurredAtDesc(doctorId, pageable);
    }

    @Transactional
    public Encounter updateContent(UUID id, UUID doctorId, boolean isStaff,
                                   UpdateEncounterRequest request) {
        Encounter encounter = requireWriter(id, doctorId, isStaff);
        encounter.updateClinicalContent(request.chiefComplaint(), request.notes());
        return encounter;
    }

    @Transactional
    public Encounter addDiagnosis(UUID id, UUID doctorId, boolean isStaff,
                                  AddDiagnosisRequest request) {
        Encounter encounter = requireWriter(id, doctorId, isStaff);
        encounter.addDiagnosis(request.code(), request.description());
        return encounter;
    }

    @Transactional
    public Encounter addPrescription(UUID id, UUID doctorId, boolean isStaff,
                                     AddPrescriptionRequest request) {
        Encounter encounter = requireWriter(id, doctorId, isStaff);
        encounter.addPrescription(request.medication(), request.dosage(), request.frequency(),
                request.durationDays(), request.instructions());
        return encounter;
    }

    @Transactional
    public Encounter sign(UUID id, UUID doctorId, boolean isStaff) {
        Encounter encounter = requireWriter(id, doctorId, isStaff);
        encounter.sign();
        log.info("encounter signed id={}", id);
        return encounter;
    }

    @Transactional
    public Encounter amend(UUID id, UUID doctorId, boolean isStaff, AmendRequest request) {
        Encounter encounter = requireWriter(id, doctorId, isStaff);
        encounter.amend(request.notes(), request.reason());
        log.info("encounter amended id={}", id);
        return encounter;
    }

    /** Only the treating doctor may write clinical content (staff/admin excluded by default). */
    private Encounter requireWriter(UUID id, UUID doctorId, boolean isStaff) {
        Encounter encounter = get(id);
        if (!encounter.isTreatingDoctor(doctorId)) {
            throw new AccessDeniedException(
                    "Only the treating doctor may write to this encounter");
        }
        return encounter;
    }
}
