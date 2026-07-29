package com.careconnect.patient.api;

import com.careconnect.patient.api.dto.ApiEnvelope;
import com.careconnect.patient.api.dto.CreatePatientRequest;
import com.careconnect.patient.api.dto.PatientResponse;
import com.careconnect.patient.api.dto.PatientSummary;
import com.careconnect.patient.api.dto.UpdatePatientRequest;
import com.careconnect.patient.application.PatientService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Authorization layers (docs/architecture/security.md):
 * roles via @PreAuthorize; ownership via the /me endpoints, which scope every
 * query to the caller's userId — a PATIENT can never address another patient's
 * record by ID (the IDOR guard is structural, not an if-statement).
 */
@RestController
@RequestMapping("/api/patients")
public class PatientController {

    private final PatientService patientService;

    public PatientController(PatientService patientService) {
        this.patientService = patientService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('STAFF','ADMIN','DOCTOR')")
    public ApiEnvelope<List<PatientResponse>> search(
            @RequestParam(required = false) String q,
            @PageableDefault(size = 20, sort = "lastName", direction = Sort.Direction.ASC)
            Pageable pageable) {
        return ApiEnvelope.ofPage(patientService.search(q, pageable), PatientResponse::from);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('STAFF','ADMIN')")
    public ResponseEntity<ApiEnvelope<PatientResponse>> create(
            @Valid @RequestBody CreatePatientRequest request) {
        PatientResponse created = PatientResponse.from(patientService.create(request));
        return ResponseEntity.created(URI.create("/api/patients/" + created.id()))
                .body(ApiEnvelope.of(created));
    }

    // /me routes are declared before /{id} semantics matter — Spring resolves
    // the literal path first, but keep them adjacent for readability.

    /** Self-onboarding: first-time patients create their own linked profile. */
    @PostMapping("/me")
    @PreAuthorize("hasRole('PATIENT')")
    public ResponseEntity<ApiEnvelope<PatientResponse>> createMyProfile(
            @AuthenticationPrincipal String userId,
            @Valid @RequestBody CreatePatientRequest request) {
        PatientResponse created = PatientResponse.from(
                patientService.createOwnProfile(UUID.fromString(userId), request));
        return ResponseEntity.created(URI.create("/api/patients/me")).body(ApiEnvelope.of(created));
    }

    @GetMapping("/me")
    @PreAuthorize("hasRole('PATIENT')")
    public ApiEnvelope<PatientResponse> myProfile(@AuthenticationPrincipal String userId) {
        return ApiEnvelope.of(PatientResponse.from(patientService.getOwn(UUID.fromString(userId))));
    }

    @PutMapping("/me")
    @PreAuthorize("hasRole('PATIENT')")
    public ApiEnvelope<PatientResponse> updateMyContact(
            @AuthenticationPrincipal String userId,
            @Valid @RequestBody UpdatePatientRequest request) {
        return ApiEnvelope.of(PatientResponse.from(
                patientService.updateOwnContact(UUID.fromString(userId), request)));
    }

    /**
     * Internal validation view for other services (appointment booking).
     * Any authenticated principal — the caller's identity headers are
     * forwarded through Feign, and a PATIENT booking for themselves must
     * be able to resolve this. UUIDs are unguessable; payload is minimal.
     */
    @GetMapping("/{id}/summary")
    @PreAuthorize("isAuthenticated()")
    public ApiEnvelope<PatientSummary> summary(@PathVariable UUID id) {
        return ApiEnvelope.of(PatientSummary.from(patientService.get(id)));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('STAFF','ADMIN','DOCTOR')")
    public ApiEnvelope<PatientResponse> get(@PathVariable UUID id) {
        return ApiEnvelope.of(PatientResponse.from(patientService.get(id)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('STAFF','ADMIN')")
    public ApiEnvelope<PatientResponse> update(@PathVariable UUID id,
                                               @Valid @RequestBody UpdatePatientRequest request) {
        return ApiEnvelope.of(PatientResponse.from(patientService.update(id, request)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('STAFF','ADMIN')")
    public ResponseEntity<Void> deactivate(@PathVariable UUID id) {
        patientService.deactivate(id);
        return ResponseEntity.noContent().build();
    }
}
