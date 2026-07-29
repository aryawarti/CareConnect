package com.careconnect.billing.api.dto;

import com.careconnect.billing.domain.Invoice;
import com.careconnect.billing.domain.Payment;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class BillingDtos {

    private BillingDtos() { }

    public record PayRequest(
            @NotNull @DecimalMin("0.01") BigDecimal amount,
            @Size(max = 20) String method,
            /** Idempotency key — the client generates it once per attempt. */
            @NotBlank @Size(max = 64) String reference) {
    }

    public record VoidRequest(@NotBlank @Size(max = 300) String reason) {
    }

    public record PaymentResponse(UUID id, BigDecimal amount, String method,
                                  String reference, Instant paidAt) {
        static PaymentResponse from(Payment p) {
            return new PaymentResponse(p.getId(), p.getAmount(), p.getMethod(),
                    p.getReference(), p.getPaidAt());
        }
    }

    public record InvoiceResponse(
            UUID id, String invoiceNumber, UUID appointmentId, UUID patientId,
            String patientName, String doctorName, BigDecimal amount, String status,
            Instant issuedAt, Instant paidAt, String voidedReason,
            List<PaymentResponse> payments) {

        public static InvoiceResponse from(Invoice i) {
            return new InvoiceResponse(i.getId(), i.getInvoiceNumber(), i.getAppointmentId(),
                    i.getPatientId(), i.getPatientName(), i.getDoctorName(), i.getAmount(),
                    i.getStatus().name(), i.getIssuedAt(), i.getPaidAt(), i.getVoidedReason(),
                    i.getPayments().stream().map(PaymentResponse::from).toList());
        }
    }
}
