package com.careconnect.laboratory.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.annotation.CreatedBy;

@Entity
@Table(name = "lab_results")
public class LabResult {

    @Id @GeneratedValue
    private UUID id;

    @Column(name = "order_item_id", nullable = false)
    private UUID orderItemId;

    @Column(name = "analyte_id", nullable = false)
    private UUID analyteId;

    @Column(name = "analyte_name", nullable = false)
    private String analyteName;

    @Column(nullable = false)
    private String value;

    private String unit;

    @Column(name = "ref_low")  private BigDecimal refLow;
    @Column(name = "ref_high") private BigDecimal refHigh;

    @Enumerated(EnumType.STRING)
    private ResultFlag flag;

    @CreatedBy @Column(name = "entered_by")
    private String enteredBy;

    @Column(name = "entered_at", nullable = false)
    private Instant enteredAt = Instant.now();

    protected LabResult() { }

    public LabResult(UUID orderItemId, UUID analyteId, String analyteName, String value,
                     String unit, BigDecimal refLow, BigDecimal refHigh, ResultFlag flag) {
        this.orderItemId = orderItemId;
        this.analyteId = analyteId;
        this.analyteName = analyteName;
        this.value = value;
        this.unit = unit;
        this.refLow = refLow;
        this.refHigh = refHigh;
        this.flag = flag;
    }

    public UUID getId() { return id; }
    public UUID getOrderItemId() { return orderItemId; }
    public String getAnalyteName() { return analyteName; }
    public String getValue() { return value; }
    public String getUnit() { return unit; }
    public BigDecimal getRefLow() { return refLow; }
    public BigDecimal getRefHigh() { return refHigh; }
    public ResultFlag getFlag() { return flag; }
    public boolean isCritical() { return flag != null && flag.isCritical(); }
}
