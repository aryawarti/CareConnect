package com.careconnect.appointment.api.dto;

import com.careconnect.appointment.domain.Appointment;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public final class AppointmentDtos {

    private AppointmentDtos() { }

    public record BookRequest(
            @NotNull UUID doctorId,
            /** Ignored for PATIENT callers — they always book for themselves. */
            UUID patientId,
            @NotNull @Future Instant startAt,
            @Size(max = 500) String reason) {
    }

    public record AppointmentResponse(
            UUID id, UUID patientId, UUID doctorId,
            String patientName, String doctorName,
            Instant startAt, Instant endAt,
            String status, String reason, BigDecimal feeSnapshot) {

        public static AppointmentResponse from(Appointment a) {
            return new AppointmentResponse(a.getId(), a.getPatientId(), a.getDoctorId(),
                    a.getPatientName(), a.getDoctorName(), a.getStartAt(), a.getEndAt(),
                    a.getStatus().name(), a.getReason(), a.getFeeSnapshot());
        }
    }

    public record FreeSlot(Instant startAt, Instant endAt) { }
}
