package com.careconnect.medicalrecord.api;

import com.careconnect.medicalrecord.api.dto.ApiEnvelope;
import com.careconnect.medicalrecord.api.dto.RecordDtos.AddDiagnosisRequest;
import com.careconnect.medicalrecord.api.dto.RecordDtos.AddPrescriptionRequest;
import com.careconnect.medicalrecord.api.dto.RecordDtos.AmendRequest;
import com.careconnect.medicalrecord.api.dto.RecordDtos.EncounterResponse;
import com.careconnect.medicalrecord.api.dto.RecordDtos.UpdateEncounterRequest;
import com.careconnect.medicalrecord.application.MedicalRecordService;
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
    private final PatientClient patientClient;
    private final ProviderClient providerClient;

    public MedicalRecordController(MedicalRecordService service, PatientClient patientClient,
                                   ProviderClient providerClient) {
        this.service = service;
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

    @GetMapping("/me")
    @PreAuthorize("hasRole('PATIENT')")
    public ApiEnvelope<List<EncounterResponse>> myHistory(
            @PageableDefault(size = 20) Pageable pageable) {
        return ApiEnvelope.ofPage(service.forPatient(resolveOwnPatientId(), pageable),
                EncounterResponse::summary);
    }

    @GetMapping("/patient/{patientId}")
    @PreAuthorize("hasAnyRole('DOCTOR','STAFF','ADMIN')")
    public ApiEnvelope<List<EncounterResponse>> patientHistory(
            @PathVariable UUID patientId,
            @PageableDefault(size = 20) Pageable pageable) {
        return ApiEnvelope.ofPage(service.forPatient(patientId, pageable),
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
    @Transactional(readOnly = true)   // DTO mapping below touches lazy collections
    public ApiEnvelope<EncounterResponse> get(@PathVariable UUID id,
                                              @AuthenticationPrincipal String userId,
                                              Authentication auth) {
        return ApiEnvelope.of(EncounterResponse.from(service.getForReader(
                id, resolveReaderId(userId, auth), isStaff(auth), hasRole(auth, "PATIENT"))));
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
