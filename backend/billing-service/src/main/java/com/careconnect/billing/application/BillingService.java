package com.careconnect.billing.application;

import com.careconnect.billing.domain.Invoice;
import com.careconnect.billing.domain.InvoiceNotFoundException;
import com.careconnect.billing.domain.InvoiceStatus;
import com.careconnect.billing.domain.Payment;
import com.careconnect.billing.infrastructure.messaging.DomainEventPublisher;
import com.careconnect.billing.infrastructure.messaging.KafkaTopicsConfig;
import com.careconnect.billing.infrastructure.repository.InvoiceRepository;
import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BillingService {

    private static final Logger log = LoggerFactory.getLogger(BillingService.class);

    private final InvoiceRepository invoices;
    private final DomainEventPublisher events;
    private final Counter issued;
    private final Counter paid;

    public BillingService(InvoiceRepository invoices, DomainEventPublisher events,
                          MeterRegistry meters) {
        this.invoices = invoices;
        this.events = events;
        this.issued = Counter.builder("careconnect.invoices.issued")
                .description("Invoices generated from completed appointments").register(meters);
        this.paid = Counter.builder("careconnect.invoices.paid")
                .description("Invoices settled").register(meters);
    }

    /**
     * Invoice generation from a completed visit. The fee comes from the event
     * (snapshotted at booking time by appointment-service) — billing never
     * asks provider-service for a *current* fee, because the price a patient
     * agreed to is the price they pay (ADR-004, snapshot rule).
     */
    @Transactional
    public Invoice issueForCompletedAppointment(UUID appointmentId, UUID patientId, UUID doctorId,
                                                String patientName, String doctorName,
                                                BigDecimal amount) {
        return invoices.findByAppointmentId(appointmentId).orElseGet(() -> {
            String number = "INV-%06d".formatted(invoices.nextInvoiceNumber());
            Invoice invoice = invoices.save(new Invoice(number, appointmentId, patientId,
                    doctorId, patientName, doctorName, amount));
            log.info("invoice issued {} amount={} appointment={}", number, amount, appointmentId);
            publish("InvoiceIssued", invoice);
            issued.increment();
            return invoice;
        });
    }

    @Transactional(readOnly = true)
    public Invoice get(UUID id) {
        return invoices.findById(id).orElseThrow(() -> new InvoiceNotFoundException(id));
    }

    @Transactional(readOnly = true)
    public Invoice getForReader(UUID id, UUID callerId, boolean isStaff) {
        Invoice invoice = get(id);
        if (!isStaff && !invoice.belongsTo(callerId)) {
            throw new AccessDeniedException("This invoice belongs to another patient");
        }
        return invoice;
    }

    @Transactional(readOnly = true)
    public Page<Invoice> forPatient(UUID patientId, Pageable pageable) {
        return invoices.findByPatientIdOrderByIssuedAtDesc(patientId, pageable);
    }

    @Transactional(readOnly = true)
    public Page<Invoice> byStatus(InvoiceStatus status, Pageable pageable) {
        return invoices.findByStatusOrderByIssuedAtDesc(status, pageable);
    }

    /**
     * Simulated settlement. `reference` is the idempotency key: a duplicate
     * submit (double-clicked button, retried request) hits the unique
     * constraint instead of paying twice — the same discipline a real gateway
     * integration requires.
     */
    @Transactional
    public Invoice pay(UUID invoiceId, UUID callerId, boolean isStaff,
                       BigDecimal amount, String method, String reference) {
        Invoice invoice = get(invoiceId);
        if (!isStaff && !invoice.belongsTo(callerId)) {
            throw new AccessDeniedException("This invoice belongs to another patient");
        }
        Payment payment = invoice.pay(amount, method == null ? "SIMULATED" : method, reference);
        log.info("invoice {} paid ref={}", invoice.getInvoiceNumber(), payment.getReference());
        publish("InvoicePaid", invoice);
        paid.increment();
        return invoice;
    }

    @Transactional
    public Invoice voidInvoice(UUID invoiceId, String reason) {
        Invoice invoice = get(invoiceId);
        invoice.voidInvoice(reason);
        log.info("invoice {} voided: {}", invoice.getInvoiceNumber(), reason);
        publish("InvoiceVoided", invoice);
        return invoice;
    }

    private void publish(String type, Invoice invoice) {
        events.publish(KafkaTopicsConfig.BILLING_EVENTS, type, invoice.getId(), Map.of(
                "invoiceId", invoice.getId().toString(),
                "invoiceNumber", invoice.getInvoiceNumber(),
                "appointmentId", invoice.getAppointmentId().toString(),
                "patientId", invoice.getPatientId().toString(),
                "patientName", invoice.getPatientName(),
                "doctorName", invoice.getDoctorName(),
                "amount", invoice.getAmount().toPlainString(),
                "status", invoice.getStatus().name()));
    }
}
