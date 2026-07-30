package com.careconnect.queue.api.dto;

import com.careconnect.queue.domain.QueueEntry;
import com.careconnect.queue.domain.QueuePriority;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class QueueDtos {

    private QueueDtos() { }

    /**
     * Check-in from a booked appointment (staff or patient self check-in).
     *
     * {@code patientId} is honoured only for staff callers — a PATIENT is always
     * checked in as themselves, resolved from their own account, so the field
     * cannot be used to place someone else in a queue (see QueueController).
     *
     * Display names are deliberately absent: they are resolved server-side from
     * patient-service and provider-service, not accepted from the caller.
     */
    public record CheckInRequest(
            UUID appointmentId,
            UUID patientId,
            @NotNull UUID doctorId,
            @Size(max = 300) String complaint,
            QueuePriority priority) {
    }

    /** Walk-in: no appointment, staff registers them straight into the queue. */
    public record WalkInRequest(
            @NotNull UUID patientId,
            @NotNull UUID doctorId,
            @Size(max = 160) String patientName,
            @Size(max = 160) String doctorName,
            @Size(max = 300) String complaint,
            QueuePriority priority) {
    }

    public record QueueEntryResponse(
            UUID id, UUID appointmentId, UUID patientId, UUID doctorId,
            String patientName, String doctorName, String tokenNumber,
            String priority, String status, String complaint,
            Instant checkedInAt, Instant calledAt, Instant startedAt,
            long waitedMinutes, int callAttempts,
            /** 0 = next to be called; null once seen. */
            Integer position,
            Integer estimatedWaitMinutes) {

        public static QueueEntryResponse from(QueueEntry e, Integer position, Integer eta) {
            return new QueueEntryResponse(e.getId(), e.getAppointmentId(), e.getPatientId(),
                    e.getDoctorId(), e.getPatientName(), e.getDoctorName(), e.getTokenNumber(),
                    e.getPriority().name(), e.getStatus().name(), e.getComplaint(),
                    e.getCheckedInAt(), e.getCalledAt(), e.getStartedAt(),
                    e.waitedMinutes(), e.getCallAttempts(), position, eta);
        }
    }

    /**
     * Everything a live screen needs in one payload. Contains PHI (full names,
     * presenting complaints) — only ever served over an authenticated request.
     * The lobby board gets {@link BoardSnapshot} instead.
     */
    public record QueueSnapshot(
            UUID doctorId,
            String doctorName,
            int waiting,
            int averageConsultationMinutes,
            QueueEntryResponse nowServing,
            List<QueueEntryResponse> entries,
            Instant generatedAt) {
    }

    /**
     * The public projection, for the unauthenticated lobby board and its SSE
     * stream.
     *
     * The board is deliberately anonymous: a token number, a given name so a
     * patient recognises their turn, and nothing else. No surname, no patient
     * id, and above all no presenting complaint — a waiting-room TV must never
     * announce why someone is here.
     *
     * This projection exists because redaction has to happen on the server. The
     * board component used to receive the full entry and render only part of
     * it, which protects nobody: the payload was one devtools tab away.
     */
    public record BoardEntry(
            UUID id,
            String tokenNumber,
            String status,
            String priority,
            /** Given name only — "Asha", never "Asha Verma". */
            String givenName,
            Integer position,
            Integer estimatedWaitMinutes) {

        public static BoardEntry from(QueueEntryResponse full) {
            return new BoardEntry(full.id(), full.tokenNumber(), full.status(),
                    full.priority(), givenNameOf(full.patientName()),
                    full.position(), full.estimatedWaitMinutes());
        }

        private static String givenNameOf(String fullName) {
            if (fullName == null || fullName.isBlank()) {
                return "";
            }
            return fullName.trim().split("\\s+")[0];
        }
    }

    public record BoardSnapshot(
            UUID doctorId,
            String doctorName,
            int waiting,
            int averageConsultationMinutes,
            BoardEntry nowServing,
            List<BoardEntry> entries,
            Instant generatedAt) {

        /** Redacts a full snapshot down to what a lobby screen may see. */
        public static BoardSnapshot from(QueueSnapshot full) {
            return new BoardSnapshot(full.doctorId(), full.doctorName(), full.waiting(),
                    full.averageConsultationMinutes(),
                    full.nowServing() == null ? null : BoardEntry.from(full.nowServing()),
                    full.entries().stream().map(BoardEntry::from).toList(),
                    full.generatedAt());
        }
    }

    /** The patient's own live view: "you are 3rd, about 25 minutes". */
    public record MyQueueStatus(
            boolean inQueue,
            QueueEntryResponse entry,
            String message) {
    }
}
