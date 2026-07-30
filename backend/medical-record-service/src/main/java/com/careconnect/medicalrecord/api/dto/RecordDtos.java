package com.careconnect.medicalrecord.api.dto;

import com.careconnect.medicalrecord.domain.Amendment;
import com.careconnect.medicalrecord.domain.Diagnosis;
import com.careconnect.medicalrecord.domain.Encounter;
import com.careconnect.medicalrecord.domain.Prescription;
import com.careconnect.medicalrecord.domain.RecordAccessEntry;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class RecordDtos {

    private RecordDtos() { }

    public record UpdateEncounterRequest(
            @Size(max = 500) String chiefComplaint,
            @Size(max = 20000) String notes) {
    }

    public record AddDiagnosisRequest(
            @NotBlank @Size(max = 20) String code,
            @NotBlank @Size(max = 300) String description) {
    }

    public record AddPrescriptionRequest(
            @NotBlank @Size(max = 200) String medication,
            @NotBlank @Size(max = 100) String dosage,
            @NotBlank @Size(max = 100) String frequency,
            @Min(1) @Max(365) int durationDays,
            @Size(max = 500) String instructions) {
    }

    public record AmendRequest(
            @NotBlank @Size(max = 20000) String notes,
            @NotBlank @Size(max = 500) String reason) {
    }

    public record DiagnosisResponse(UUID id, String code, String description) {
        static DiagnosisResponse from(Diagnosis d) {
            return new DiagnosisResponse(d.getId(), d.getCode(), d.getDescription());
        }
    }

    public record PrescriptionResponse(UUID id, String medication, String dosage,
                                       String frequency, int durationDays, String instructions) {
        static PrescriptionResponse from(Prescription p) {
            return new PrescriptionResponse(p.getId(), p.getMedication(), p.getDosage(),
                    p.getFrequency(), p.getDurationDays(), p.getInstructions());
        }
    }

    public record AmendmentResponse(String previousNote, String reason, Instant amendedAt) {
        static AmendmentResponse from(Amendment a) {
            return new AmendmentResponse(a.getPreviousNote(), a.getReason(), a.getAmendedAt());
        }
    }

    public record EncounterResponse(
            UUID id, UUID appointmentId, UUID patientId, UUID doctorId,
            String patientName, String doctorName, Instant occurredAt,
            String chiefComplaint, String notes, String status,
            List<DiagnosisResponse> diagnoses,
            List<PrescriptionResponse> prescriptions,
            List<AmendmentResponse> amendments) {

        public static EncounterResponse from(Encounter e) {
            return new EncounterResponse(e.getId(), e.getAppointmentId(), e.getPatientId(),
                    e.getDoctorId(), e.getPatientName(), e.getDoctorName(), e.getOccurredAt(),
                    e.getChiefComplaint(), e.getNotes(), e.getStatus().name(),
                    e.getDiagnoses().stream().map(DiagnosisResponse::from).toList(),
                    e.getPrescriptions().stream().map(PrescriptionResponse::from).toList(),
                    e.getAmendments().stream().map(AmendmentResponse::from).toList());
        }

        /** Summary view for lists: no clinical detail, just the visit header. */
        public static EncounterResponse summary(Encounter e) {
            return new EncounterResponse(e.getId(), e.getAppointmentId(), e.getPatientId(),
                    e.getDoctorId(), e.getPatientName(), e.getDoctorName(), e.getOccurredAt(),
                    e.getChiefComplaint(), null, e.getStatus().name(),
                    List.of(), List.of(), List.of());
        }
    }

    /**
     * One entry in the chart access trail.
     *
     * Carries the actor's account id and email but no clinical content — the log
     * answers "who looked", and a record of reads that itself discloses the data
     * would be self-defeating.
     */
    public record AccessLogEntryResponse(
            UUID id,
            UUID actorUserId,
            String actorRole,
            String actorName,
            UUID patientId,
            UUID encounterId,
            String action,
            boolean selfAccess,
            Instant accessedAt,
            String correlationId) {

        public static AccessLogEntryResponse from(RecordAccessEntry e) {
            return new AccessLogEntryResponse(e.getId(), e.getActorUserId(), e.getActorRole(),
                    e.getActorName(), e.getPatientId(), e.getEncounterId(),
                    e.getAction().name(), e.isSelfAccess(), e.getAccessedAt(),
                    e.getCorrelationId());
        }
    }
}
