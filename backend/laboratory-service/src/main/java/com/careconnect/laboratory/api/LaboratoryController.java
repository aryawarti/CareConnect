package com.careconnect.laboratory.api;

import com.careconnect.laboratory.api.dto.ApiEnvelope;
import com.careconnect.laboratory.api.dto.LabDtos.CatalogueResponse;
import com.careconnect.laboratory.api.dto.LabDtos.CollectRequest;
import com.careconnect.laboratory.api.dto.LabDtos.CreateOrderRequest;
import com.careconnect.laboratory.api.dto.LabDtos.EnterResultsRequest;
import com.careconnect.laboratory.api.dto.LabDtos.OrderResponse;
import com.careconnect.laboratory.api.dto.LabDtos.RejectRequest;
import com.careconnect.laboratory.application.LaboratoryService;
import com.careconnect.laboratory.infrastructure.client.PatientClient;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/lab")
public class LaboratoryController {

    private final LaboratoryService service;
    private final PatientClient patientClient;

    public LaboratoryController(LaboratoryService service, PatientClient patientClient) {
        this.service = service;
        this.patientClient = patientClient;
    }

    @GetMapping("/catalogue")
    @PreAuthorize("hasAnyRole('DOCTOR','LAB_TECHNICIAN','STAFF','ADMIN')")
    public ApiEnvelope<List<CatalogueResponse>> catalogue(@RequestParam(required = false) String q) {
        return ApiEnvelope.of(service.catalogue(q).stream().map(CatalogueResponse::from).toList());
    }

    /** Doctor orders tests from within an encounter; billable immediately. */
    @PostMapping("/orders")
    @PreAuthorize("hasRole('DOCTOR')")
    @Transactional   // DTO mapping below touches lazy collections; not read-only, this writes
    public ApiEnvelope<OrderResponse> order(
            @AuthenticationPrincipal String userId,
            @RequestHeader(value = "X-User-Email", required = false) String email,
            @Valid @RequestBody CreateOrderRequest request) {
        // patient/doctor display names are resolved from the encounter context on the
        // client and passed through; here we use the doctor's own identity as the doctor.
        var order = service.order(request, UUID.fromString(userId),
                request.clinicalIndication() == null ? "Patient" : "Patient",
                email == null ? "Doctor" : email);
        return ApiEnvelope.of(OrderResponse.summary(order));
    }

    @GetMapping("/orders")
    @PreAuthorize("hasAnyRole('LAB_TECHNICIAN','ADMIN','STAFF')")
    @Transactional(readOnly = true)   // DTO mapping below touches lazy collections
    public ApiEnvelope<List<OrderResponse>> worklist() {
        return ApiEnvelope.of(service.worklist().stream().map(OrderResponse::summary).toList());
    }

    @GetMapping("/orders/{id}")
    @PreAuthorize("hasAnyRole('LAB_TECHNICIAN','DOCTOR','ADMIN','STAFF')")
    public ApiEnvelope<OrderResponse> detail(@PathVariable UUID id) {
        return ApiEnvelope.of(service.detail(id));
    }

    @PostMapping("/orders/{id}/collection")
    @PreAuthorize("hasAnyRole('LAB_TECHNICIAN','NURSE')")
    public ApiEnvelope<OrderResponse> collect(@PathVariable UUID id,
                                              @Valid @RequestBody CollectRequest request) {
        service.collect(id, request.specimenType(), null);
        return ApiEnvelope.of(service.detail(id));
    }

    @PostMapping("/orders/{id}/processing")
    @PreAuthorize("hasRole('LAB_TECHNICIAN')")
    public ApiEnvelope<OrderResponse> process(@PathVariable UUID id) {
        service.beginProcessing(id);
        return ApiEnvelope.of(service.detail(id));
    }

    @PostMapping("/orders/{id}/rejection")
    @PreAuthorize("hasRole('LAB_TECHNICIAN')")
    public ApiEnvelope<OrderResponse> reject(@PathVariable UUID id,
                                             @Valid @RequestBody RejectRequest request) {
        service.reject(id, request.reason());
        return ApiEnvelope.of(service.detail(id));
    }

    @PostMapping("/orders/{id}/results")
    @PreAuthorize("hasRole('LAB_TECHNICIAN')")
    public ApiEnvelope<OrderResponse> enterResults(@PathVariable UUID id,
                                                   @Valid @RequestBody EnterResultsRequest request) {
        service.enterResults(id, request);
        return ApiEnvelope.of(service.detail(id));
    }

    /** Senior verification releases the report to the patient. */
    @PostMapping("/orders/{id}/verification")
    @PreAuthorize("hasAnyRole('LAB_TECHNICIAN','ADMIN')")
    public ApiEnvelope<OrderResponse> verify(@PathVariable UUID id,
                                             @AuthenticationPrincipal String userId) {
        service.verify(id, userId);
        return ApiEnvelope.of(service.detail(id));
    }

    // ---- patient & doctor reads --------------------------------------------

    @GetMapping("/me")
    @PreAuthorize("hasRole('PATIENT')")
    public ApiEnvelope<List<OrderResponse>> myReports(
            @PageableDefault(size = 20) Pageable pageable) {
        // Lab orders are keyed by patient-service's patient id, not the
        // identity user id — resolve through patient-service (ADR-004).
        // Patients see only released (VERIFIED) reports.
        UUID patientId = patientClient.me().data().id();
        return ApiEnvelope.of(service.forPatient(patientId, true, pageable)
                .map(o -> service.detail(o.getId())).getContent());
    }

    @GetMapping("/patient/{patientId}")
    @PreAuthorize("hasAnyRole('DOCTOR','LAB_TECHNICIAN','STAFF','ADMIN')")
    public ApiEnvelope<List<OrderResponse>> patientOrders(
            @PathVariable UUID patientId,
            @PageableDefault(size = 20) Pageable pageable) {
        return ApiEnvelope.of(service.forPatient(patientId, false, pageable)
                .map(o -> service.detail(o.getId())).getContent());
    }
}
