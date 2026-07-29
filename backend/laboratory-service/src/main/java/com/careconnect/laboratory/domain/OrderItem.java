package com.careconnect.laboratory.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;

/** One test within an order. Carries a price snapshot so billing never drifts. */
@Entity
@Table(name = "lab_order_items")
public class OrderItem {

    @Id @GeneratedValue
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id")
    private LabOrder order;

    @Column(name = "test_id", nullable = false)
    private UUID testId;

    @Column(name = "test_code", nullable = false)
    private String testCode;

    @Column(name = "test_name", nullable = false)
    private String testName;

    @Column(name = "price_snapshot", nullable = false)
    private BigDecimal priceSnapshot;

    @Column(nullable = false)
    private String status = "ORDERED";

    protected OrderItem() { }

    OrderItem(LabOrder order, UUID testId, String testCode, String testName, BigDecimal price) {
        this.order = order;
        this.testId = testId;
        this.testCode = testCode;
        this.testName = testName;
        this.priceSnapshot = price;
    }

    public UUID getId() { return id; }
    public UUID getTestId() { return testId; }
    public String getTestCode() { return testCode; }
    public String getTestName() { return testName; }
    public BigDecimal getPriceSnapshot() { return priceSnapshot; }
}
