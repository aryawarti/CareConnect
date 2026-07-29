package com.careconnect.provider.api.dto;

import com.careconnect.provider.domain.AvailabilitySlot;
import com.careconnect.provider.domain.Doctor;
import com.careconnect.provider.domain.ScheduleException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

/** Provider API contracts, grouped: one file per aggregate keeps the api/dto package navigable. */
public final class DoctorDtos {

    private DoctorDtos() { }

    public record CreateDoctorRequest(
            @NotBlank @Size(max = 80) String firstName,
            @NotBlank @Size(max = 80) String lastName,
            @NotBlank @Size(max = 120) String specialty,
            @NotNull UUID departmentId,
            @NotNull @DecimalMin("0.00") BigDecimal consultationFee,
            @Size(max = 255) String email,
            @Size(max = 25) String phone,
            UUID userId) {
    }

    public record UpdateDoctorRequest(
            @Size(max = 80) String firstName,
            @Size(max = 80) String lastName,
            @Size(max = 120) String specialty,
            UUID departmentId,
            @DecimalMin("0.00") BigDecimal consultationFee,
            @Size(max = 255) String email,
            @Size(max = 25) String phone) {
    }

    /** A doctor applying to join the hospital themselves. */
    public record DoctorApplicationRequest(
            @NotBlank @Size(max = 80) String firstName,
            @NotBlank @Size(max = 80) String lastName,
            @NotBlank @Size(max = 120) String specialty,
            @NotNull UUID departmentId,
            @NotBlank @Size(max = 160) String qualification,
            @NotBlank @Size(max = 60) String registrationNo,
            @Min(0) @Max(70) Integer experienceYears,
            @Size(max = 600) String bio,
            @DecimalMin("0.00") BigDecimal consultationFee,
            @Size(max = 25) String phone) {
    }

    public record RejectRequest(@NotBlank @Size(max = 300) String reason) {
    }

    public record DoctorResponse(
            UUID id, String firstName, String lastName, String specialty,
            UUID departmentId, String departmentName, BigDecimal consultationFee,
            String email, String phone, String status,
            String verification, String qualification, String registrationNo,
            Integer experienceYears, String bio, String rejectionReason) {

        public static DoctorResponse from(Doctor d) {
            return new DoctorResponse(d.getId(), d.getFirstName(), d.getLastName(),
                    d.getSpecialty(), d.getDepartment().getId(), d.getDepartment().getName(),
                    d.getConsultationFee(), d.getEmail(), d.getPhone(), d.getStatus().name(),
                    d.getVerification().name(), d.getQualification(), d.getRegistrationNo(),
                    d.getExperienceYears() == null ? null : d.getExperienceYears().intValue(),
                    d.getBio(), d.getRejectionReason());
        }
    }

    public record SlotRequest(
            @Min(1) @Max(7) int dayOfWeek,
            @NotNull LocalTime startTime,
            @NotNull LocalTime endTime,
            @Min(5) @Max(240) int slotMinutes) {
    }

    public record ReplaceAvailabilityRequest(@NotNull List<@Valid SlotRequest> slots) {
    }

    public record SlotResponse(UUID id, int dayOfWeek, LocalTime startTime,
                               LocalTime endTime, int slotMinutes) {

        public static SlotResponse from(AvailabilitySlot s) {
            return new SlotResponse(s.getId(), s.getDayOfWeek(), s.getStartTime(),
                    s.getEndTime(), s.getSlotMinutes());
        }
    }

    public record ExceptionRequest(@NotNull LocalDate date, @Size(max = 200) String reason) {
    }

    public record ExceptionResponse(UUID id, LocalDate date, String reason) {

        public static ExceptionResponse from(ScheduleException e) {
            return new ExceptionResponse(e.getId(), e.getExceptionDate(), e.getReason());
        }
    }
}
