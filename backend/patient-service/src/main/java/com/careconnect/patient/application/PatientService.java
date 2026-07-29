package com.careconnect.patient.application;

import com.careconnect.patient.api.dto.CreatePatientRequest;
import com.careconnect.patient.api.dto.UpdatePatientRequest;
import com.careconnect.patient.domain.Patient;
import com.careconnect.patient.domain.PatientNotFoundException;
import com.careconnect.patient.domain.ProfileAlreadyExistsException;
import com.careconnect.patient.infrastructure.messaging.DomainEventPublisher;
import com.careconnect.patient.infrastructure.messaging.KafkaTopicsConfig;
import com.careconnect.patient.infrastructure.repository.PatientRepository;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PatientService {

    private static final Logger log = LoggerFactory.getLogger(PatientService.class);

    private final PatientRepository patients;
    private final DomainEventPublisher events;

    public PatientService(PatientRepository patients, DomainEventPublisher events) {
        this.patients = patients;
        this.events = events;
    }

    @Transactional
    public Patient create(CreatePatientRequest request) {
        // MRN from a DB sequence: unique under concurrency without app-level locking.
        String mrn = "P-%06d".formatted(patients.nextPatientNumber());
        Patient patient = new Patient(mrn, request.firstName(), request.lastName(),
                request.dateOfBirth(), request.gender());
        apply(patient, request.toUpdate());
        if (request.userId() != null) {
            patient.linkUser(request.userId());
        }
        patients.save(patient);
        log.info("patient created id={} mrn={}", patient.getId(), mrn);
        events.publish(KafkaTopicsConfig.PATIENT_EVENTS, "PatientRegistered", patient.getId(), Map.of(
                "patientId", patient.getId().toString(),
                "patientNumber", patient.getPatientNumber(),
                "fullName", patient.getFirstName() + " " + patient.getLastName(),
                "email", patient.getEmail() == null ? "" : patient.getEmail()));
        return patient;
    }

    @Transactional(readOnly = true)
    public Patient get(UUID id) {
        return patients.findById(id).orElseThrow(() -> new PatientNotFoundException(id));
    }

    @Transactional(readOnly = true)
    public Patient getOwn(UUID userId) {
        return patients.findByUserId(userId)
                .orElseThrow(() -> new PatientNotFoundException(
                        "No patient profile linked to your account yet"));
    }

    @Transactional(readOnly = true)
    public Page<Patient> search(String query, Pageable pageable) {
        return (query == null || query.isBlank())
                ? patients.findAll(pageable)
                : patients.search(query.trim(), pageable);
    }

    @Transactional
    public Patient update(UUID id, UpdatePatientRequest request) {
        Patient patient = get(id);
        apply(patient, request);
        return patient;
    }

    /**
     * Self-onboarding (FR-B4 companion): a self-registered user creates their
     * own patient record, linked to their identity. One per account.
     */
    @Transactional
    public Patient createOwnProfile(UUID userId, CreatePatientRequest request) {
        if (patients.existsByUserId(userId)) {
            throw new ProfileAlreadyExistsException();
        }
        Patient patient = create(new CreatePatientRequest(request.firstName(),
                request.lastName(), request.dateOfBirth(), request.gender(),
                request.phone(), request.email(), request.address(),
                request.emergencyContactName(), request.emergencyContactPhone(), userId));
        return patient;
    }

    /** Patients may edit their own contact data — never their clinical identity (name/DOB). */
    @Transactional
    public Patient updateOwnContact(UUID userId, UpdatePatientRequest request) {
        Patient patient = getOwn(userId);
        patient.setPhone(request.phone());
        patient.setEmail(request.email());
        patient.setAddress(request.address());
        patient.setEmergencyContactName(request.emergencyContactName());
        patient.setEmergencyContactPhone(request.emergencyContactPhone());
        return patient;
    }

    @Transactional
    public void deactivate(UUID id) {
        Patient patient = get(id);
        patient.deactivate();
        log.info("patient deactivated id={}", id);
    }

    private void apply(Patient patient, UpdatePatientRequest request) {
        if (request.firstName() != null) patient.setFirstName(request.firstName());
        if (request.lastName() != null) patient.setLastName(request.lastName());
        if (request.dateOfBirth() != null) patient.setDateOfBirth(request.dateOfBirth());
        if (request.gender() != null) patient.setGender(request.gender());
        patient.setPhone(request.phone());
        patient.setEmail(request.email());
        patient.setAddress(request.address());
        patient.setEmergencyContactName(request.emergencyContactName());
        patient.setEmergencyContactPhone(request.emergencyContactPhone());
    }
}
