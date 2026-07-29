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

    /** Check-in from a booked appointment (staff or patient self check-in). */
    public record CheckInRequest(
            UUID appointmentId,
            UUID patientId,
            UUID doctorId,
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

    /** Everything a live screen needs in one payload. */
    public record QueueSnapshot(
            UUID doctorId,
            String doctorName,
            int waiting,
            int averageConsultationMinutes,
            QueueEntryResponse nowServing,
            List<QueueEntryResponse> entries,
            Instant generatedAt) {
    }

    /** The patient's own live view: "you are 3rd, about 25 minutes". */
    public record MyQueueStatus(
            boolean inQueue,
            QueueEntryResponse entry,
            String message) {
    }
}
