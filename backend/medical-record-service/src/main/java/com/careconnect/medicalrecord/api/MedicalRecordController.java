package com.careconnect.medicalrecord.api;

import com.careconnect.medicalrecord.api.dto.ApiEnvelope;
import com.careconnect.medicalrecord.api.dto.RecordDtos.AddDiagnosisRequest;
import com.careconnect.medicalrecord.api.dto.RecordDtos.AddPrescriptionRequest;
import com.careconnect.medicalrecord.api.dto.RecordDtos.AmendRequest;
import com.careconnect.medicalrecord.api.dto.RecordDtos.EncounterResponse;
import com.careconnect.medicalrecord.api.dto.RecordDtos.UpdateEncounterRequest;
import com.careconnect.medicalrecord.api.dto.RecordDtos.AccessLogEntryResponse;
import com.careconnect.medicalrecord.application.Actor;
import com.careconnect.medicalrecord.application.MedicalRecordService;
import com.careconnect.medicalrecord.application.RecordAccessLogger;
import com.careconnect.medicalrecord.infrastructure.client.PatientClient;
import com.careconnect.medicalrecord.infrastructure.client.ProviderClient;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Note the absence of a POST /encounters: encounters are born from
 * AppointmentCompleted events. You cannot chart a visit that never happened.
 */
@RestController
@RequestMapping("/api/records")
public class MedicalRecordController {

    private final MedicalRecordService service;
    private final RecordAccessLogger accessLog;
    private final PatientClient patientClient;
    private final ProviderClient providerClient;

    public MedicalRecordController(MedicalRecordService service, RecordAccessLogger accessLog,
                                   PatientClient patientClient, ProviderClient providerClient) {
        this.service = service;
        this.accessLog = accessLog;
        this.patientClient = patientClient;
        this.providerClient = providerClient;
    }

    private static boolean isStaff(Authentication auth) {
        return auth.getAuthorities().stream().anyMatch(a ->
                a.getAuthority().equals("ROLE_STAFF") || a.getAuthority().equals("ROLE_ADMIN"));
    }

    private static boolean hasRole(Authentication auth, String role) {
        return auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_" + role));
    }

    /** Encounters are keyed by patient-service's patient id and provider-service's
     *  doctor id — neither is the identity user id (JWT subject), so ownership
     *  checks must resolve through the owning service rather than conflating
     *  id spaces. */
    private UUID resolveOwnPatientId() {
        return patientClient.me().data().id();
    }

    private UUID resolveOwnDoctorId() {
        return providerClient.me().data().id();
    }

    /** Ownership-check id for a non-staff reader: the resolved patient id for
     *  a PATIENT, the resolved doctor id for a DOCTOR, or the raw user id for
     *  anyone else — which matches neither check, so access is still denied. */
    private UUID resolveReaderId(String userId, Authentication auth) {
        if (hasRole(auth, "DOCTOR")) { return resolveOwnDoctorId(); }
        if (hasRole(auth, "PATIENT")) { return resolveOwnPatientId(); }
        return UUID.fromString(userId);
    }

    /**
     * Who to record against in the access log. The account, not the clinical id:
     * the JWT subject is what identifies a person who can be held answerable.
     * Email comes from the gateway (X-User-Email) purely as a display label.
     */
    private Actor actor(String userId, Authentication auth,
                        @RequestHeader(value = "X-User-Email", required = false) String email) {
        String role = auth.getAuthorities().stream().findFirst()
                .map(a -> a.getAuthority().replace("ROLE_", ""))
                .orElse("UNKNOWN");
        return new Actor(UUID.fromString(userId), role, email);
    }

    @GetMapping("/me")
    @PreAuthorize("hasRole('PATIENT')")
    public ApiEnvelope<List<EncounterResponse>> myHistory(
            @AuthenticationPrincipal String userId,
            Authentication auth,
            @RequestHeader(value = "X-User-Email", required = false) String email,
            @PageableDefault(size = 20) Pageable pageable) {
        return ApiEnvelope.ofPage(
                service.forOwnHistory(resolveOwnPatientId(), pageable, actor(userId, auth, email)),
                EncounterResponse::summary);
    }

    /**
     * A patient's history for a clinician. A DOCTOR must have treated them; the
     * role by itself grants nothing (see forPatientAsReader).
     */
    @GetMapping("/patient/{patientId}")
    @PreAuthorize("hasAnyRole('DOCTOR','STAFF','ADMIN')")
    public ApiEnvelope<List<EncounterResponse>> patientHistory(
            @PathVariable UUID patientId,
            @AuthenticationPrincipal String userId,
            Authentication auth,
            @RequestHeader(value = "X-User-Email", required = false) String email,
            @PageableDefault(size = 20) Pageable pageable) {
        return ApiEnvelope.ofPage(
                service.forPatientAsReader(patientId, resolveReaderId(userId, auth),
                        isStaff(auth), pageable, actor(userId, auth, email)),
                EncounterResponse::summary);
    }

    @GetMapping("/doctor/me")
    @PreAuthorize("hasRole('DOCTOR')")
    public ApiEnvelope<List<EncounterResponse>> myEncounters(
            @PageableDefault(size = 20) Pageable pageable) {
        return ApiEnvelope.ofPage(service.forDoctor(resolveOwnDoctorId(), pageable),
                EncounterResponse::summary);
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    @Transactional   // not read-only: opening a chart writes an access-log entry
    public ApiEnvelope<EncounterResponse> get(@PathVariable UUID id,
                                              @AuthenticationPrincipal String userId,
                                              Authentication auth,
                                              @RequestHeader(value = "X-User-Email",
                                                             required = false) String email) {
        return ApiEnvelope.of(EncounterResponse.from(service.getForReader(
                id, resolveReaderId(userId, auth), isStaff(auth), hasRole(auth, "PATIENT"),
                actor(userId, auth, email))));
    }

    // ---- chart access trail --------------------------------------------------

    /**
     * "Who has looked at my records." Patient-facing, and the reason this log is
     * worth building rather than just logging to a file nobody reads: the person
     * the data is about can see who opened it.
     */
    @GetMapping("/access-log/me")
    @PreAuthorize("hasRole('PATIENT')")
    public ApiEnvelope<List<AccessLogEntryResponse>> myAccessLog(
            @PageableDefault(size = 50) Pageable pageable) {
        return ApiEnvelope.ofPage(accessLog.forPatient(resolveOwnPatientId(), pageable),
                AccessLogEntryResponse::from);
    }

    /** The audit view: every read of one patient's chart. */
    @GetMapping("/access-log/patient/{patientId}")
    @PreAuthorize("hasAnyRole('ADMIN','STAFF')")
    public ApiEnvelope<List<AccessLogEntryResponse>> patientAccessLog(
            @PathVariable UUID patientId,
            @PageableDefault(size = 50) Pageable pageable) {
        return ApiEnvelope.ofPage(accessLog.forPatient(patientId, pageable),
                AccessLogEntryResponse::from);
    }

    /**
     * Everything one account has read. The question asked when an account is
     * suspected of browsing records it has no business in — which is the whole
     * point of recording the actor rather than just the patient.
     */
    @GetMapping("/access-log/actor/{actorUserId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiEnvelope<List<AccessLogEntryResponse>> actorAccessLog(
            @PathVariable UUID actorUserId,
            @PageableDefault(size = 50) Pageable pageable) {
        return ApiEnvelope.ofPage(accessLog.byActor(actorUserId, pageable),
                AccessLogEntryResponse::from);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('DOCTOR')")
    @Transactional   // DTO mapping below touches lazy collections; not read-only, this writes
    public ApiEnvelope<EncounterResponse> update(@PathVariable UUID id,
                                                 Authentication auth,
                                                 @Valid @RequestBody UpdateEncounterRequest request) {
        return ApiEnvelope.of(EncounterResponse.from(
                service.updateContent(id, resolveOwnDoctorId(), isStaff(auth), request)));
    }

    @PostMapping("/{id}/diagnoses")
    @PreAuthorize("hasRole('DOCTOR')")
    @Transactional
    public ApiEnvelope<EncounterResponse> addDiagnosis(@PathVariable UUID id,
                                                       Authentication auth,
                                                       @Valid @RequestBody AddDiagnosisRequest request) {
        return ApiEnvelope.of(EncounterResponse.from(
                service.addDiagnosis(id, resolveOwnDoctorId(), isStaff(auth), request)));
    }

    @PostMapping("/{id}/prescriptions")
    @PreAuthorize("hasRole('DOCTOR')")
    @Transactional
    public ApiEnvelope<EncounterResponse> addPrescription(@PathVariable UUID id,
                                                          Authentication auth,
                                                          @Valid @RequestBody AddPrescriptionRequest request) {
        return ApiEnvelope.of(EncounterResponse.from(
                service.addPrescription(id, resolveOwnDoctorId(), isStaff(auth), request)));
    }

    @PostMapping("/{id}/signature")
    @PreAuthorize("hasRole('DOCTOR')")
    @Transactional
    public ApiEnvelope<EncounterResponse> sign(@PathVariable UUID id,
                                               Authentication auth) {
        return ApiEnvelope.of(EncounterResponse.from(
                service.sign(id, resolveOwnDoctorId(), isStaff(auth))));
    }

    @PostMapping("/{id}/amendments")
    @PreAuthorize("hasRole('DOCTOR')")
    @Transactional
    public ApiEnvelope<EncounterResponse> amend(@PathVariable UUID id,
                                                Authentication auth,
                                                @Valid @RequestBody AmendRequest request) {
        return ApiEnvelope.of(EncounterResponse.from(
                service.amend(id, resolveOwnDoctorId(), isStaff(auth), request)));
    }
}
