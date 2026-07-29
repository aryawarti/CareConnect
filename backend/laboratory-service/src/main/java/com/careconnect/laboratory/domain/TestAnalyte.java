package com.careconnect.laboratory.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "test_analytes")
public class TestAnalyte {

    @Id @GeneratedValue
    private UUID id;

    @Column(name = "test_id", nullable = false)
    private UUID testId;

    @Column(nullable = false)
    private String name;

    private String unit;

    @Column(name = "ref_low")       private BigDecimal refLow;
    @Column(name = "ref_high")      private BigDecimal refHigh;
    @Column(name = "critical_low")  private BigDecimal criticalLow;
    @Column(name = "critical_high") private BigDecimal criticalHigh;

    @Column(name = "display_order", nullable = false)
    private short displayOrder;

    protected TestAnalyte() { }

    public UUID getId() { return id; }
    public String getName() { return name; }
    public String getUnit() { return unit; }
    public BigDecimal getRefLow() { return refLow; }
    public BigDecimal getRefHigh() { return refHigh; }
    public BigDecimal getCriticalLow() { return criticalLow; }
    public BigDecimal getCriticalHigh() { return criticalHigh; }
    public short getDisplayOrder() { return displayOrder; }
}
