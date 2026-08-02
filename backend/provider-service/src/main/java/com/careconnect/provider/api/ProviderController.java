package com.careconnect.provider.api;

import com.careconnect.provider.api.dto.ApiEnvelope;
import com.careconnect.provider.api.dto.BookingInfo;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import com.careconnect.provider.api.dto.DoctorDtos;
import com.careconnect.provider.api.dto.DoctorDtos.CreateDoctorRequest;
import com.careconnect.provider.api.dto.DoctorDtos.DepartmentSummary;
import com.careconnect.provider.api.dto.DoctorDtos.DirectoryEntry;
import com.careconnect.provider.api.dto.DoctorDtos.DoctorProfileResponse;
import com.careconnect.provider.api.dto.DoctorDtos.DoctorResponse;
import com.careconnect.provider.api.dto.DoctorDtos.ExceptionRequest;
import com.careconnect.provider.api.dto.DoctorDtos.ExceptionResponse;
import com.careconnect.provider.api.dto.DoctorDtos.ReplaceAvailabilityRequest;
import com.careconnect.provider.api.dto.DoctorDtos.SlotResponse;
import com.careconnect.provider.api.dto.DoctorDtos.UpdateDoctorRequest;
import com.careconnect.provider.api.dto.DoctorSummary;
import com.careconnect.provider.application.ProviderService;
import com.careconnect.provider.domain.Doctor;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/providers")
public class ProviderController {

    private final ProviderService service;

    public ProviderController(ProviderService service) {
        this.service = service;
    }

    private static boolean isStaff(Authentication auth) {
        return auth.getAuthorities().stream().anyMatch(a ->
                a.getAuthority().equals("ROLE_STAFF") || a.getAuthority().equals("ROLE_ADMIN"));
    }

    // ---- public directory (FR-C3) -----------------------------------------

    /**
     * The doctor list a patient browses, optionally narrowed to one department.
     *
     * Each entry carries the doctor's working days, so a card can say "Mon, Wed,
     * Fri" or "no schedule published" instead of implying every doctor is
     * bookable and letting the patient discover otherwise at the date picker.
     */
    @GetMapping("/directory")
    public ApiEnvelope<List<DirectoryEntry>> directory(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) UUID departmentId,
            @PageableDefault(size = 20, sort = "lastName", direction = Sort.Direction.ASC)
            Pageable pageable) {
        Page<Doctor> page = service.directory(q, departmentId, pageable);
        Map<UUID, Set<Integer>> workingDays =
                service.workingDaysFor(page.getContent().stream().map(Doctor::getId).toList());
        return ApiEnvelope.ofPage(page, d -> DirectoryEntry.from(d, workingDays.get(d.getId())));
    }

    @GetMapping("/departments")
    public ApiEnvelope<List<DepartmentSummary>> departments() {
        Map<UUID, Long> counts = service.doctorCountsByDepartment();
        return ApiEnvelope.of(service.departments().stream()
                .map(d -> new DepartmentSummary(d.getId(), d.getName(),
                        counts.getOrDefault(d.getId(), 0L)))
                .toList());
    }

    /**
     * Everything the doctor profile page shows, in one call: credentials, fee,
     * the weekly pattern and upcoming time off. Assembled server-side so the
     * page renders at once rather than in four instalments.
     */
    @GetMapping("/doctors/{id}/profile")
    @PreAuthorize("isAuthenticated()")
    public ApiEnvelope<DoctorProfileResponse> profile(@PathVariable UUID id) {
        var view = service.profile(id);
        return ApiEnvelope.of(DoctorProfileResponse.from(
                view.doctor(), view.weekly(), view.timeOff()));
    }

    // ---- self-registration + verification ----------------------------------

    /**
     * A doctor applies to join the hospital using their own account.
     * Requires the DOCTOR role (assigned at signup) — the application itself
     * is what an administrator then verifies.
     */
    @PostMapping("/apply")
    @PreAuthorize("hasRole('DOCTOR')")
    public ResponseEntity<ApiEnvelope<DoctorResponse>> apply(
            @AuthenticationPrincipal String userId,
            @RequestHeader(value = "X-User-Email", required = false) String email,
            @Valid @RequestBody DoctorDtos.DoctorApplicationRequest request) {
        DoctorResponse created = DoctorResponse.from(
                service.apply(UUID.fromString(userId), request, email));
        return ResponseEntity.created(URI.create("/api/providers/me"))
                .body(ApiEnvelope.of(created));
    }

    /** Applications waiting for an administrator's decision. */
    @GetMapping("/applications")
    @PreAuthorize("hasAnyRole('ADMIN','STAFF')")
    public ApiEnvelope<List<DoctorResponse>> applications() {
        return ApiEnvelope.of(service.pendingApplications().stream()
                .map(DoctorResponse::from).toList());
    }

    @PostMapping("/doctors/{id}/approval")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiEnvelope<DoctorResponse> approve(@PathVariable UUID id) {
        return ApiEnvelope.of(DoctorResponse.from(service.approve(id)));
    }

    @PostMapping("/doctors/{id}/rejection")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiEnvelope<DoctorResponse> reject(@PathVariable UUID id,
                                              @Valid @RequestBody DoctorDtos.RejectRequest request) {
        return ApiEnvelope.of(DoctorResponse.from(service.reject(id, request.reason())));
    }

    /** Every doctor including pending/rejected — administration view. */
    @GetMapping("/doctors")
    @PreAuthorize("hasAnyRole('ADMIN','STAFF')")
    public ApiEnvelope<List<DoctorResponse>> allDoctors(
            @PageableDefault(size = 100) Pageable pageable) {
        return ApiEnvelope.ofPage(service.all(pageable), DoctorResponse::from);
    }

    // ---- doctor management -------------------------------------------------

    @PostMapping("/doctors")
    @PreAuthorize("hasAnyRole('STAFF','ADMIN')")
    public ResponseEntity<ApiEnvelope<DoctorResponse>> create(
            @Valid @RequestBody CreateDoctorRequest request) {
        DoctorResponse created = DoctorResponse.from(service.create(request));
        return ResponseEntity.created(URI.create("/api/providers/doctors/" + created.id()))
                .body(ApiEnvelope.of(created));
    }

    @GetMapping("/me")
    @PreAuthorize("hasRole('DOCTOR')")
    public ApiEnvelope<DoctorResponse> myProfile(@AuthenticationPrincipal String userId) {
        return ApiEnvelope.of(DoctorResponse.from(service.getOwn(UUID.fromString(userId))));
    }

    @GetMapping("/doctors/{id}")
    @PreAuthorize("hasAnyRole('STAFF','ADMIN','DOCTOR')")
    public ApiEnvelope<DoctorResponse> get(@PathVariable UUID id) {
        return ApiEnvelope.of(DoctorResponse.from(service.get(id)));
    }

    @PutMapping("/doctors/{id}")
    @PreAuthorize("hasAnyRole('STAFF','ADMIN')")
    public ApiEnvelope<DoctorResponse> update(@PathVariable UUID id,
                                              @Valid @RequestBody UpdateDoctorRequest request) {
        return ApiEnvelope.of(DoctorResponse.from(service.update(id, request)));
    }

    @DeleteMapping("/doctors/{id}")
    @PreAuthorize("hasAnyRole('STAFF','ADMIN')")
    public ResponseEntity<Void> deactivate(@PathVariable UUID id) {
        service.deactivate(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Name-only view for other services (any authenticated caller), mirroring
     * patient-service's /api/patients/{id}/summary. Carries no fee and no
     * registration details, so it is safe for a patient to read — which matters
     * because a patient checking themselves in needs their doctor's name.
     */
    @GetMapping("/doctors/{id}/summary")
    @PreAuthorize("isAuthenticated()")
    public ApiEnvelope<DoctorSummary> summary(@PathVariable UUID id) {
        return ApiEnvelope.of(DoctorSummary.from(service.get(id)));
    }

    /** Internal one-call validation view for appointment-service (any authenticated). */
    @GetMapping("/doctors/{id}/booking-info")
    @PreAuthorize("isAuthenticated()")
    public ApiEnvelope<BookingInfo> bookingInfo(
            @PathVariable UUID id,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ApiEnvelope.of(service.bookingInfo(id, date));
    }

    // ---- availability ------------------------------------------------------

    @GetMapping("/doctors/{id}/availability")
    public ApiEnvelope<List<SlotResponse>> availability(@PathVariable UUID id) {
        return ApiEnvelope.of(service.availability(id).stream().map(SlotResponse::from).toList());
    }

    @PutMapping("/doctors/{id}/availability")
    @PreAuthorize("hasAnyRole('STAFF','ADMIN','DOCTOR')")
    public ApiEnvelope<List<SlotResponse>> replaceAvailability(
            @PathVariable UUID id,
            @AuthenticationPrincipal String userId,
            Authentication auth,
            @Valid @RequestBody ReplaceAvailabilityRequest request) {
        return ApiEnvelope.of(service.replaceAvailability(id, UUID.fromString(userId),
                        isStaff(auth), request)
                .stream().map(SlotResponse::from).toList());
    }

    // ---- schedule exceptions ----------------------------------------------

    @GetMapping("/doctors/{id}/exceptions")
    @PreAuthorize("hasAnyRole('STAFF','ADMIN','DOCTOR')")
    public ApiEnvelope<List<ExceptionResponse>> exceptions(@PathVariable UUID id) {
        return ApiEnvelope.of(service.upcomingExceptions(id).stream()
                .map(ExceptionResponse::from).toList());
    }

    @PostMapping("/doctors/{id}/exceptions")
    @PreAuthorize("hasAnyRole('STAFF','ADMIN','DOCTOR')")
    public ResponseEntity<ApiEnvelope<ExceptionResponse>> addException(
            @PathVariable UUID id,
            @AuthenticationPrincipal String userId,
            Authentication auth,
            @Valid @RequestBody ExceptionRequest request) {
        ExceptionResponse created = ExceptionResponse.from(
                service.addException(id, UUID.fromString(userId), isStaff(auth), request));
        return ResponseEntity.created(
                        URI.create("/api/providers/doctors/%s/exceptions/%s".formatted(id, created.id())))
                .body(ApiEnvelope.of(created));
    }

    @DeleteMapping("/doctors/{id}/exceptions/{exceptionId}")
    @PreAuthorize("hasAnyRole('STAFF','ADMIN','DOCTOR')")
    public ResponseEntity<Void> removeException(@PathVariable UUID id,
                                                @PathVariable UUID exceptionId,
                                                @AuthenticationPrincipal String userId,
                                                Authentication auth) {
        service.removeException(id, UUID.fromString(userId), isStaff(auth), exceptionId);
        return ResponseEntity.noContent().build();
    }
}
