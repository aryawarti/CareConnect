package com.careconnect.medicalrecord.application;

import com.careconnect.medicalrecord.api.dto.RecordDtos.AddDiagnosisRequest;
import com.careconnect.medicalrecord.api.dto.RecordDtos.AddPrescriptionRequest;
import com.careconnect.medicalrecord.api.dto.RecordDtos.AmendRequest;
import com.careconnect.medicalrecord.api.dto.RecordDtos.UpdateEncounterRequest;
import com.careconnect.medicalrecord.domain.Encounter;
import com.careconnect.medicalrecord.domain.EncounterNotFoundException;
import com.careconnect.medicalrecord.domain.RecordAccessAction;
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
    private final RecordAccessLogger accessLog;

    public MedicalRecordService(EncounterRepository encounters, RecordAccessLogger accessLog) {
        this.encounters = encounters;
        this.accessLog = accessLog;
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
    /**
     * Not read-only: the access log write shares this transaction, deliberately,
     * so a chart that cannot be recorded as read is not served. See
     * {@link RecordAccessLogger}.
     */
    @Transactional
    public Encounter getForReader(UUID id, UUID callerId, boolean isStaff,
                                  boolean isPatientRole, Actor actor) {
        Encounter encounter = get(id);
        boolean permitted = isStaff
                || encounter.isTreatingDoctor(callerId)
                || (isPatientRole && encounter.belongsToPatient(callerId));
        if (!permitted) {
            // A denied attempt is not logged here: nothing was disclosed, and an
            // audit trail of reads should not be padded with non-reads. Denials
            // are a security-monitoring concern and surface as 403s in the
            // access logs, not in the clinical record trail.
            throw new AccessDeniedException("You do not have access to this record");
        }
        accessLog.record(actor.userId(), actor.role(), actor.email(),
                encounter.getPatientId(), encounter.getId(),
                RecordAccessAction.VIEW_ENCOUNTER,
                isPatientRole && encounter.belongsToPatient(callerId));
        return encounter;
    }

    @Transactional(readOnly = true)
    public Page<Encounter> forPatient(UUID patientId, Pageable pageable) {
        return encounters.findByPatientIdOrderByOccurredAtDesc(patientId, pageable);
    }

    /** A patient listing their own history. Logged like any other read. */
    @Transactional
    public Page<Encounter> forOwnHistory(UUID patientId, Pageable pageable, Actor actor) {
        accessLog.record(actor.userId(), actor.role(), actor.email(), patientId, null,
                RecordAccessAction.LIST_OWN_HISTORY, true);
        return forPatient(patientId, pageable);
    }

    /**
     * A patient's history read by somebody other than the patient.
     *
     * Role alone is not enough. {@link #getForReader} already enforced that for
     * a single encounter, but the *list* endpoint behind this had no check at
     * all: any account with ROLE_DOCTOR could enumerate any patient's complete
     * clinical history by id. The relationship — has this doctor ever treated
     * this patient — is what grants access, exactly as for one encounter.
     *
     * Staff and admin are permitted for administrative work, consistent with
     * getForReader.
     */
    @Transactional
    public Page<Encounter> forPatientAsReader(UUID patientId, UUID callerId, boolean isStaff,
                                              Pageable pageable, Actor actor) {
        if (!isStaff && !encounters.existsByPatientIdAndDoctorId(patientId, callerId)) {
            throw new AccessDeniedException(
                    "You have not treated this patient, so their history is not available to you");
        }
        accessLog.record(actor.userId(), actor.role(), actor.email(), patientId, null,
                RecordAccessAction.LIST_PATIENT_HISTORY, false);
        return forPatient(patientId, pageable);
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
