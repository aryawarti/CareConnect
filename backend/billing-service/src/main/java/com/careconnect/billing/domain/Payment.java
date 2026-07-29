package com.careconnect.billing.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
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

    protected Payment() { }

    Payment(BigDecimal amount, String method, String reference) {
        this.amount = amount;
        this.method = method;
        this.reference = reference;
    }

    public UUID getId() { return id; }
    public BigDecimal getAmount() { return amount; }
    public String getMethod() { return method; }
    public String getReference() { return reference; }
    public Instant getPaidAt() { return paidAt; }
}
