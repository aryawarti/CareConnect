package com.careconnect.medicalrecord.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * Aggregate root: one clinical encounter (visit). Created from an
 * AppointmentCompleted event; enriched by the treating doctor.
 */
@Entity
@Table(name = "encounters")
@EntityListeners(AuditingEntityListener.class)
public class Encounter {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "appointment_id", nullable = false, unique = true, updatable = false)
    private UUID appointmentId;

    @Column(name = "patient_id", nullable = false, updatable = false)
    private UUID patientId;

    @Column(name = "doctor_id", nullable = false, updatable = false)
    private UUID doctorId;

    @Column(name = "patient_name", nullable = false, updatable = false)
    private String patientName;

    @Column(name = "doctor_name", nullable = false, updatable = false)
    private String doctorName;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    @Column(name = "chief_complaint")
    private String chiefComplaint;

    @Column(columnDefinition = "text")
    private String notes;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EncounterStatus status = EncounterStatus.OPEN;

    @OneToMany(mappedBy = "encounter", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Diagnosis> diagnoses = new ArrayList<>();

    @OneToMany(mappedBy = "encounter", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Prescription> prescriptions = new ArrayList<>();

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "encounter_id")
    private List<Amendment> amendments = new ArrayList<>();

    @CreatedDate  @Column(name = "created_at", updatable = false) private Instant createdAt;
    @LastModifiedDate @Column(name = "updated_at") private Instant updatedAt;
    @CreatedBy    @Column(name = "created_by", updatable = false) private String createdBy;
    @LastModifiedBy @Column(name = "updated_by") private String updatedBy;

    protected Encounter() { }

    public Encounter(UUID appointmentId, UUID patientId, UUID doctorId,
                     String patientName, String doctorName, Instant occurredAt) {
        this.appointmentId = appointmentId;
        this.patientId = patientId;
        this.doctorId = doctorId;
        this.patientName = patientName;
        this.doctorName = doctorName;
        this.occurredAt = occurredAt;
    }

    /** Free editing only while OPEN (FR-E2). */
    public void updateClinicalContent(String chiefComplaint, String notes) {
        requireOpen();
        this.chiefComplaint = chiefComplaint;
        this.notes = notes;
    }

    public void addDiagnosis(String code, String description) {
        requireOpen();
        diagnoses.add(new Diagnosis(this, code, description));
    }

    public void addPrescription(String medication, String dosage, String frequency,
                                int durationDays, String instructions) {
        requireOpen();
        prescriptions.add(new Prescription(this, medication, dosage, frequency,
                durationDays, instructions));
    }

    public void sign() {
        requireOpen();
        if (notes == null || notes.isBlank()) {
            throw new IllegalStateException("An encounter cannot be signed without notes");
        }
        this.status = EncounterStatus.SIGNED;
    }

    /**
     * Post-signature correction: the previous text is preserved with a reason,
     * and the record moves to AMENDED. Clinical records are corrected by
     * addition, never by erasure.
     */
    public void amend(String newNotes, String reason) {
        if (status == EncounterStatus.OPEN) {
            throw new IllegalStateException("Open encounters are edited directly, not amended");
        }
        amendments.add(new Amendment(this.notes, reason));
        this.notes = newNotes;
        this.status = EncounterStatus.AMENDED;
    }

    public boolean isTreatingDoctor(UUID doctorUserId) {
        return doctorId.equals(doctorUserId);
    }

    public boolean belongsToPatient(UUID patient) {
        return patientId.equals(patient);
    }

    private void requireOpen() {
        if (status != EncounterStatus.OPEN) {
            throw new IllegalStateException(
                    "Encounter is %s — use an amendment to change it".formatted(status));
        }
    }

    public UUID getId() { return id; }
    public UUID getAppointmentId() { return appointmentId; }
    public UUID getPatientId() { return patientId; }
    public UUID getDoctorId() { return doctorId; }
    public String getPatientName() { return patientName; }
    public String getDoctorName() { return doctorName; }
    public Instant getOccurredAt() { return occurredAt; }
    public String getChiefComplaint() { return chiefComplaint; }
    public String getNotes() { return notes; }
    public EncounterStatus getStatus() { return status; }
    public List<Diagnosis> getDiagnoses() { return diagnoses; }
    public List<Prescription> getPrescriptions() { return prescriptions; }
    public List<Amendment> getAmendments() { return amendments; }
}
