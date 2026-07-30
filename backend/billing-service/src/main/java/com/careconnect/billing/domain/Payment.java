package com.careconnect.billing.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Entity
@Table(name = "payments")
@EntityListeners(AuditingEntityListener.class)
public class Payment {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(nullable = false)
    private BigDecimal amount;

    @Column(nullable = false)
    private String method;

    /** Client-supplied idempotency key; unique per payment attempt. */
    @Column(nullable = false, unique = true)
    private String reference;

    @Column(name = "paid_at", nullable = false)
    private Instant paidAt = Instant.now();

    @CreatedBy
    @Column(name = "recorded_by")
    private String recordedBy;

    /**
     * Owning side of the association.
     *
     * It has to be: with a unidirectional {@code @OneToMany} + {@code @JoinColumn}
     * on the parent, Hibernate INSERTs the child with a null FK and then issues a
     * separate UPDATE to set it — which the {@code NOT NULL} on
     * {@code payments.invoice_id} rejects outright. Recording a payment failed
     * against a real database every time. Making Payment own the FK means it is
     * written on the INSERT itself.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "invoice_id", nullable = false, updatable = false)
    private Invoice invoice;

    protected Payment() { }

    Payment(Invoice invoice, BigDecimal amount, String method, String reference) {
        this.invoice = invoice;
        this.amount = amount;
        this.method = method;
        this.reference = reference;
    }

    public Invoice getInvoice() { return invoice; }
    public UUID getId() { return id; }
    public BigDecimal getAmount() { return amount; }
    public String getMethod() { return method; }
    public String getReference() { return reference; }
    public Instant getPaidAt() { return paidAt; }
}
