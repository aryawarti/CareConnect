package com.careconnect.billing.api;

import com.careconnect.billing.api.dto.ApiEnvelope;
import com.careconnect.billing.api.dto.BillingDtos.InvoiceResponse;
import com.careconnect.billing.api.dto.BillingDtos.PayRequest;
import com.careconnect.billing.api.dto.BillingDtos.VoidRequest;
import com.careconnect.billing.application.BillingService;
import com.careconnect.billing.domain.InvoiceStatus;
import com.careconnect.billing.infrastructure.client.PatientClient;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** No POST /invoices: invoices are generated from AppointmentCompleted events. */
@RestController
@RequestMapping("/api/invoices")
public class BillingController {

    private final BillingService service;
    private final PatientClient patientClient;

    public BillingController(BillingService service, PatientClient patientClient) {
        this.service = service;
        this.patientClient = patientClient;
    }

    private static boolean isStaff(Authentication auth) {
        return auth.getAuthorities().stream().anyMatch(a ->
                a.getAuthority().equals("ROLE_STAFF") || a.getAuthority().equals("ROLE_ADMIN"));
    }

    private static boolean isPatient(Authentication auth) {
        return auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_PATIENT"));
    }

    /** Invoices are keyed by patient-service's patient id, not the identity
     *  user id (JWT subject) — resolve through patient-service rather than
     *  conflating the two id spaces. */
    private UUID resolveOwnPatientId() {
        return patientClient.me().data().id();
    }

    /** Ownership-check id for non-staff callers: the real patient id for a
     *  PATIENT caller, or the raw user id for anyone else (e.g. DOCTOR) —
     *  which never matches a real patient id, so the ownership check still
     *  denies correctly without an unnecessary (and rejected) Feign call. */
    private UUID resolveCallerId(String userId, Authentication auth) {
        return isPatient(auth) ? resolveOwnPatientId() : UUID.fromString(userId);
    }

    @GetMapping("/me")
    @PreAuthorize("hasRole('PATIENT')")
    @Transactional(readOnly = true)   // DTO mapping below touches the lazy payments collection
    public ApiEnvelope<List<InvoiceResponse>> myInvoices(
            @PageableDefault(size = 20) Pageable pageable) {
        return ApiEnvelope.ofPage(service.forPatient(resolveOwnPatientId(), pageable),
                InvoiceResponse::from);
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('STAFF','ADMIN')")
    @Transactional(readOnly = true)
    public ApiEnvelope<List<InvoiceResponse>> list(
            @RequestParam(defaultValue = "ISSUED") InvoiceStatus status,
            @PageableDefault(size = 20) Pageable pageable) {
        return ApiEnvelope.ofPage(service.byStatus(status, pageable), InvoiceResponse::from);
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    @Transactional(readOnly = true)
    public ApiEnvelope<InvoiceResponse> get(@PathVariable UUID id,
                                            @AuthenticationPrincipal String userId,
                                            Authentication auth) {
        return ApiEnvelope.of(InvoiceResponse.from(
                service.getForReader(id, resolveCallerId(userId, auth), isStaff(auth))));
    }

    @PostMapping("/{id}/payments")
    @PreAuthorize("hasAnyRole('PATIENT','STAFF','ADMIN')")
    @Transactional   // DTO mapping below touches lazy collections; not read-only, this writes
    public ApiEnvelope<InvoiceResponse> pay(@PathVariable UUID id,
                                            @AuthenticationPrincipal String userId,
                                            Authentication auth,
                                            @Valid @RequestBody PayRequest request) {
        return ApiEnvelope.of(InvoiceResponse.from(service.pay(id, resolveCallerId(userId, auth),
                isStaff(auth), request.amount(), request.method(), request.reference())));
    }

    @PostMapping("/{id}/void")
    @PreAuthorize("hasAnyRole('STAFF','ADMIN')")
    @Transactional
    public ApiEnvelope<InvoiceResponse> voidInvoice(@PathVariable UUID id,
                                                    @Valid @RequestBody VoidRequest request) {
        return ApiEnvelope.of(InvoiceResponse.from(service.voidInvoice(id, request.reason())));
    }
}
