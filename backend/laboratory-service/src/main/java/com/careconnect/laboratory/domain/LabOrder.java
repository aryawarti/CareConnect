package com.careconnect.laboratory.domain;

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

/**
 * Aggregate root for a laboratory order. Its status is only ever changed through
 * the transition methods, so {@link OrderStatus}'s rules are the single source
 * of what is legal — you cannot enter results before collecting a sample, or
 * verify before results exist.
 */
@Entity
@Table(name = "lab_orders")
@EntityListeners(AuditingEntityListener.class)
public class LabOrder {

    @Id @GeneratedValue
    private UUID id;

    @Column(name = "order_number", nullable = false, unique = true, updatable = false)
    private String orderNumber;

    @Column(name = "encounter_id", updatable = false)
    private UUID encounterId;

    @Column(name = "patient_id", nullable = false, updatable = false)
    private UUID patientId;

    @Column(name = "doctor_id", nullable = false, updatable = false)
    private UUID doctorId;

    @Column(name = "patient_name", nullable = false, updatable = false)
    private String patientName;

    @Column(name = "doctor_name", nullable = false, updatable = false)
    private String doctorName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private LabPriority priority = LabPriority.ROUTINE;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderStatus status = OrderStatus.ORDERED;

    @Column(name = "clinical_indication")
    private String clinicalIndication;

    @Column(name = "ordered_at", nullable = false, updatable = false)
    private Instant orderedAt = Instant.now();

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrderItem> items = new ArrayList<>();

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "order_id")
    private List<Sample> samples = new ArrayList<>();

    @CreatedDate  @Column(name = "created_at", updatable = false) private Instant createdAt;
    @LastModifiedDate @Column(name = "updated_at") private Instant updatedAt;
    @CreatedBy    @Column(name = "created_by", updatable = false) private String createdBy;
    @LastModifiedBy @Column(name = "updated_by") private String updatedBy;

    protected LabOrder() { }

    public LabOrder(String orderNumber, UUID encounterId, UUID patientId, UUID doctorId,
                    String patientName, String doctorName, LabPriority priority,
                    String clinicalIndication) {
        this.orderNumber = orderNumber;
        this.encounterId = encounterId;
        this.patientId = patientId;
        this.doctorId = doctorId;
        this.patientName = patientName;
        this.doctorName = doctorName;
        this.priority = priority == null ? LabPriority.ROUTINE : priority;
        this.clinicalIndication = clinicalIndication;
    }

    public OrderItem addTest(UUID testId, String code, String name, BigDecimal price) {
        OrderItem item = new OrderItem(this, testId, code, name, price);
        items.add(item);
        return item;
    }

    /** Collecting binds a barcoded sample to this order and advances the status. */
    public Sample collectSample(String accessionNo, String specimenType, String by) {
        transition(OrderStatus.COLLECTED);
        Sample sample = new Sample(this, accessionNo, specimenType);
        sample.markCollected(by);
        samples.add(sample);
        return sample;
    }

    public void beginProcessing() {
        transition(OrderStatus.IN_PROCESS);
    }

    public void markReported() {
        transition(OrderStatus.REPORTED);
    }

    public void verify() {
        transition(OrderStatus.VERIFIED);
    }

    public void reject(String reason) {
        if (status != OrderStatus.COLLECTED && status != OrderStatus.IN_PROCESS) {
            throw new LabException("Only a collected or processing sample can be rejected");
        }
        samples.forEach(s -> s.reject(reason));
        this.status = OrderStatus.REJECTED;
    }

    public BigDecimal totalPrice() {
        return items.stream().map(OrderItem::getPriceSnapshot)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private void transition(OrderStatus target) {
        if (!status.canMoveTo(target)) {
            throw new LabException("Cannot move order from %s to %s".formatted(status, target));
        }
        this.status = target;
    }

    public UUID getId() { return id; }
    public String getOrderNumber() { return orderNumber; }
    public UUID getEncounterId() { return encounterId; }
    public UUID getPatientId() { return patientId; }
    public UUID getDoctorId() { return doctorId; }
    public String getPatientName() { return patientName; }
    public String getDoctorName() { return doctorName; }
    public LabPriority getPriority() { return priority; }
    public OrderStatus getStatus() { return status; }
    public String getClinicalIndication() { return clinicalIndication; }
    public Instant getOrderedAt() { return orderedAt; }
    public List<OrderItem> getItems() { return items; }
    public List<Sample> getSamples() { return samples; }
}
