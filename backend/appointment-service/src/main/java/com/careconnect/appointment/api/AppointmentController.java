package com.careconnect.appointment.api;

import com.careconnect.appointment.api.dto.ApiEnvelope;
import com.careconnect.appointment.api.dto.AppointmentDtos.AppointmentResponse;
import com.careconnect.appointment.api.dto.AppointmentDtos.BookRequest;
import com.careconnect.appointment.api.dto.AppointmentDtos.FreeSlot;
import com.careconnect.appointment.application.AppointmentService;
import jakarta.validation.Valid;
import java.net.URI;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/appointments")
public class AppointmentController {

    private final AppointmentService service;

    public AppointmentController(AppointmentService service) {
        this.service = service;
    }

    private static boolean isStaff(Authentication auth) {
        return auth.getAuthorities().stream().anyMatch(a ->
                a.getAuthority().equals("ROLE_STAFF") || a.getAuthority().equals("ROLE_ADMIN"));
    }

    private static boolean isPatientOnly(Authentication auth) {
        return !isStaff(auth) && auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_PATIENT"));
    }

    @GetMapping("/available")
    public ApiEnvelope<List<FreeSlot>> available(
            @RequestParam UUID doctorId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ApiEnvelope.of(service.freeSlots(doctorId, date));
    }

    /** PATIENT books for self (patientId in the body is ignored); STAFF for anyone. */
    @PostMapping
    @PreAuthorize("hasAnyRole('PATIENT','STAFF','ADMIN')")
    public ResponseEntity<ApiEnvelope<AppointmentResponse>> book(
            @AuthenticationPrincipal String userId,
            Authentication auth,
            @Valid @RequestBody BookRequest request) {
        UUID targetPatient = isPatientOnly(auth)
                ? service.resolveOwnPatientId(UUID.fromString(userId))
                : request.patientId();
        if (targetPatient == null) {
            throw new IllegalArgumentException("patientId is required for staff bookings");
        }
        AppointmentResponse created = AppointmentResponse.from(service.book(request, targetPatient));
        return ResponseEntity.created(URI.create("/api/appointments/" + created.id()))
                .body(ApiEnvelope.of(created));
    }

    @GetMapping("/me")
    @PreAuthorize("hasRole('PATIENT')")
    public ApiEnvelope<List<AppointmentResponse>> myAppointments(
            @AuthenticationPrincipal String userId,
            @PageableDefault(size = 20) Pageable pageable) {
        UUID patientId = service.resolveOwnPatientId(UUID.fromString(userId));
        return ApiEnvelope.ofPage(service.forPatient(patientId, pageable),
                AppointmentResponse::from);
    }

    @GetMapping("/day")
    @PreAuthorize("hasAnyRole('STAFF','ADMIN')")
    public ApiEnvelope<List<AppointmentResponse>> clinicDay(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ApiEnvelope.of(service.clinicDay(date).stream()
                .map(AppointmentResponse::from).toList());
    }

    /**
     * A doctor's day. Staff run the clinic and may read any doctor's; a DOCTOR
     * may read only their own.
     *
     * The doctorId comes from the URL, so it is caller-supplied and proves
     * nothing — without the comparison below, any doctor could read a
     * colleague's full day including every patient's name and reason for visit.
     */
    @GetMapping("/doctor/{doctorId}")
    @PreAuthorize("hasAnyRole('STAFF','ADMIN','DOCTOR')")
    public ApiEnvelope<List<AppointmentResponse>> doctorDay(
            @PathVariable UUID doctorId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @AuthenticationPrincipal String userId,
            Authentication auth) {
        if (!isStaff(auth) && !service.resolveOwnDoctorId(UUID.fromString(userId)).equals(doctorId)) {
            throw new AccessDeniedException("This schedule belongs to another doctor");
        }
        return ApiEnvelope.of(service.doctorDay(doctorId, date).stream()
                .map(AppointmentResponse::from).toList());
    }

    /** A doctor's own inbox: requests waiting for them to accept or decline. */
    @GetMapping("/doctor/requests")
    @PreAuthorize("hasRole('DOCTOR')")
    public ApiEnvelope<List<AppointmentResponse>> myRequests(
            @AuthenticationPrincipal String userId) {
        UUID doctorId = service.resolveOwnDoctorId(UUID.fromString(userId));
        return ApiEnvelope.of(service.pendingRequestsForDoctor(doctorId).stream()
                .map(AppointmentResponse::from).toList());
    }

    /** Doctor accepts a request for themselves. */
    @PostMapping("/{id}/acceptance")
    @PreAuthorize("hasRole('DOCTOR')")
    public ApiEnvelope<AppointmentResponse> accept(@PathVariable UUID id,
                                                   @AuthenticationPrincipal String userId) {
        UUID doctorId = service.resolveOwnDoctorId(UUID.fromString(userId));
        return ApiEnvelope.of(AppointmentResponse.from(service.confirmAsDoctor(id, doctorId)));
    }

    /** Doctor declines (e.g. wrong specialty, unavailable) — patient is notified. */
    @PostMapping("/{id}/decline")
    @PreAuthorize("hasRole('DOCTOR')")
    public ApiEnvelope<AppointmentResponse> decline(@PathVariable UUID id,
                                                    @AuthenticationPrincipal String userId) {
        UUID doctorId = service.resolveOwnDoctorId(UUID.fromString(userId));
        return ApiEnvelope.of(AppointmentResponse.from(service.declineAsDoctor(id, doctorId)));
    }

    // -- lifecycle transitions as sub-resources (guidelines.md: no verbs in paths) --

    @PostMapping("/{id}/confirmation")
    @PreAuthorize("hasAnyRole('STAFF','ADMIN')")
    public ApiEnvelope<AppointmentResponse> confirm(@PathVariable UUID id) {
        return ApiEnvelope.of(AppointmentResponse.from(service.confirm(id)));
    }

    @PostMapping("/{id}/completion")
    @PreAuthorize("hasAnyRole('STAFF','ADMIN','DOCTOR')")
    public ApiEnvelope<AppointmentResponse> complete(@PathVariable UUID id) {
        return ApiEnvelope.of(AppointmentResponse.from(service.complete(id)));
    }

    @PostMapping("/{id}/no-show")
    @PreAuthorize("hasAnyRole('STAFF','ADMIN','DOCTOR')")
    public ApiEnvelope<AppointmentResponse> noShow(@PathVariable UUID id) {
        return ApiEnvelope.of(AppointmentResponse.from(service.noShow(id)));
    }

    @PostMapping("/{id}/cancellation")
    @PreAuthorize("hasAnyRole('PATIENT','STAFF','ADMIN')")
    public ApiEnvelope<AppointmentResponse> cancel(@PathVariable UUID id,
                                                   @AuthenticationPrincipal String userId,
                                                   Authentication auth) {
        if (isStaff(auth)) {
            return ApiEnvelope.of(AppointmentResponse.from(service.cancelAsStaff(id)));
        }
        UUID patientId = service.resolveOwnPatientId(UUID.fromString(userId));
        return ApiEnvelope.of(AppointmentResponse.from(service.cancelAsPatient(id, patientId)));
    }
}
