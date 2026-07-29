package com.careconnect.laboratory.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * A physical specimen, identified by its accession barcode. The barcode is the
 * only thing that binds a tube to a patient — scanning, never typing, is what
 * prevents the single most dangerous lab error: giving one patient's result to
 * another.
 */
@Entity
@Table(name = "lab_samples")
public class Sample {

    @Id @GeneratedValue
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id")
    private LabOrder order;

    @Column(name = "accession_no", nullable = false, unique = true)
    private String accessionNo;

    @Column(name = "specimen_type", nullable = false)
    private String specimenType;

    @Column(name = "collected_by")
    private String collectedBy;

    @Column(name = "collected_at")
    private Instant collectedAt;

    @Column(name = "rejected_reason")
    private String rejectedReason;

    protected Sample() { }

    Sample(LabOrder order, String accessionNo, String specimenType) {
        this.order = order;
        this.accessionNo = accessionNo;
        this.specimenType = specimenType;
    }

    void markCollected(String by) {
        this.collectedBy = by;
        this.collectedAt = Instant.now();
    }

    void reject(String reason) {
        this.rejectedReason = reason;
    }

    public String getAccessionNo() { return accessionNo; }
    public String getSpecimenType() { return specimenType; }
    public Instant getCollectedAt() { return collectedAt; }
    public String getRejectedReason() { return rejectedReason; }
}
