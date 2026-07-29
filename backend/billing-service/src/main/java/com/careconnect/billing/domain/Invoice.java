package com.careconnect.billing.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Entity
@Table(name = "invoices")
@EntityListeners(AuditingEntityListener.class)
public class Invoice {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "invoice_number", nullable = false, unique = true, updatable = false)
    private String invoiceNumber;

    @Column(name = "appointment_id", nullable = false, unique = true, updatable = false)
    private UUID appointmentId;

    @Column(name = "patient_id", nullable = false, updatable = false)
    private UUID patientId;

    @Column(name = "doctor_id", nullable = false, updatable = false)
    private UUID doctorId;

    @Column(name = "patient_name", nullable = false, updatable = false)
    private String patientName;

    @Column(name = "doctor_name", nullable = false, updatable = false)
    private String doctorName;

    /** BigDecimal, never double: binary floating point cannot represent money. */
    @Column(nullable = false, updatable = false)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private InvoiceStatus status = InvoiceStatus.ISSUED;

    @Column(name = "issued_at", nullable = false, updatable = false)
    private Instant issuedAt = Instant.now();

    @Column(name = "paid_at")
    private Instant paidAt;

    @Column(name = "voided_reason")
    private String voidedReason;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "invoice_id")
    private List<Payment> payments = new ArrayList<>();

    @CreatedDate  @Column(name = "created_at", updatable = false) private Instant createdAt;
    @LastModifiedDate @Column(name = "updated_at") private Instant updatedAt;
    @CreatedBy    @Column(name = "created_by", updatable = false) private String createdBy;
    @LastModifiedBy @Column(name = "updated_by") private String updatedBy;

    protected Invoice() { }

    public Invoice(String invoiceNumber, UUID appointmentId, UUID patientId, UUID doctorId,
                   String patientName, String doctorName, BigDecimal amount) {
        this.invoiceNumber = invoiceNumber;
        this.appointmentId = appointmentId;
        this.patientId = patientId;
        this.doctorId = doctorId;
        this.patientName = patientName;
        this.doctorName = doctorName;
        this.amount = amount;
    }

    /**
     * Records a payment. The reference is the idempotency key: a retried
     * payment request with the same reference is rejected by the unique
     * constraint rather than charging twice.
     */
    public Payment pay(BigDecimal amount, String method, String reference) {
        if (status == InvoiceStatus.PAID) {
            throw new InvoiceStateException("Invoice %s is already paid".formatted(invoiceNumber));
        }
        if (status == InvoiceStatus.VOID) {
            throw new InvoiceStateException("Invoice %s was voided".formatted(invoiceNumber));
        }
        if (amount.compareTo(this.amount) != 0) {
            // v1 is settle-in-full; partial payments would need a balance model.
            throw new InvoiceStateException(
                    "Partial payments are not supported — expected %s".formatted(this.amount));
        }
        Payment payment = new Payment(amount, method, reference);
        payments.add(payment);
        this.status = InvoiceStatus.PAID;
        this.paidAt = Instant.now();
        return payment;
    }

    public void voidInvoice(String reason) {
        if (status == InvoiceStatus.PAID) {
            throw new InvoiceStateException("A paid invoice cannot be voided — issue a refund");
        }
        this.status = InvoiceStatus.VOID;
        this.voidedReason = reason;
    }

    public boolean belongsTo(UUID patient) {
        return patientId.equals(patient);
    }

    public UUID getId() { return id; }
    public String getInvoiceNumber() { return invoiceNumber; }
    public UUID getAppointmentId() { return appointmentId; }
    public UUID getPatientId() { return patientId; }
    public UUID getDoctorId() { return doctorId; }
    public String getPatientName() { return patientName; }
    public String getDoctorName() { return doctorName; }
    public BigDecimal getAmount() { return amount; }
    public InvoiceStatus getStatus() { return status; }
    public Instant getIssuedAt() { return issuedAt; }
    public Instant getPaidAt() { return paidAt; }
    public String getVoidedReason() { return voidedReason; }
    public List<Payment> getPayments() { return payments; }
}
