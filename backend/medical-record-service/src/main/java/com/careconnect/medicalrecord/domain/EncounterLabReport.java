package com.careconnect.medicalrecord.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** A link from an encounter/patient to a released lab report (owned by laboratory-service). */
@Entity
@Table(name = "encounter_lab_reports")
public class EncounterLabReport {

    @Id @GeneratedValue
    private UUID id;

    @Column(name = "encounter_id")
    private UUID encounterId;

    @Column(name = "patient_id", nullable = false)
    private UUID patientId;

    @Column(name = "order_id", nullable = false, unique = true)
    private UUID orderId;

    @Column(name = "order_number", nullable = false)
    private String orderNumber;

    @Column(name = "released_at", nullable = false)
    private Instant releasedAt = Instant.now();

    protected EncounterLabReport() { }

    public EncounterLabReport(UUID encounterId, UUID patientId, UUID orderId, String orderNumber) {
        this.encounterId = encounterId;
        this.patientId = patientId;
        this.orderId = orderId;
        this.orderNumber = orderNumber;
    }

    public UUID getOrderId() { return orderId; }
    public String getOrderNumber() { return orderNumber; }
    public Instant getReleasedAt() { return releasedAt; }
}
