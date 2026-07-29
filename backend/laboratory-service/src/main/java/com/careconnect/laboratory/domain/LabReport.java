package com.careconnect.laboratory.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** Released report reference: the point after which a patient may see results. */
@Entity
@Table(name = "lab_reports")
public class LabReport {

    @Id @GeneratedValue
    private UUID id;

    @Column(name = "order_id", nullable = false, unique = true)
    private UUID orderId;

    @Column(name = "file_key")
    private String fileKey;

    @Column(name = "released_at", nullable = false)
    private Instant releasedAt = Instant.now();

    @Column(name = "released_by")
    private String releasedBy;

    protected LabReport() { }

    public LabReport(UUID orderId, String fileKey, String releasedBy) {
        this.orderId = orderId;
        this.fileKey = fileKey;
        this.releasedBy = releasedBy;
    }

    public UUID getOrderId() { return orderId; }
    public Instant getReleasedAt() { return releasedAt; }
}
