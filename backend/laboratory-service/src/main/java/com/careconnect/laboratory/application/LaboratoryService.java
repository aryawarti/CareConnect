package com.careconnect.laboratory.application;

import com.careconnect.laboratory.api.dto.LabDtos.CreateOrderRequest;
import com.careconnect.laboratory.api.dto.LabDtos.EnterResultsRequest;
import com.careconnect.laboratory.api.dto.LabDtos.OrderItemResponse;
import com.careconnect.laboratory.api.dto.LabDtos.OrderResponse;
import com.careconnect.laboratory.api.dto.LabDtos.ResultResponse;
import com.careconnect.laboratory.domain.LabException;
import com.careconnect.laboratory.domain.LabOrder;
import com.careconnect.laboratory.domain.LabReport;
import com.careconnect.laboratory.domain.LabResult;
import com.careconnect.laboratory.domain.OrderItem;
import com.careconnect.laboratory.domain.OrderNotFoundException;
import com.careconnect.laboratory.domain.OrderStatus;
import com.careconnect.laboratory.domain.ResultFlag;
import com.careconnect.laboratory.domain.Sample;
import com.careconnect.laboratory.domain.TestAnalyte;
import com.careconnect.laboratory.domain.TestCatalogue;
import com.careconnect.laboratory.infrastructure.messaging.DomainEventPublisher;
import com.careconnect.laboratory.infrastructure.messaging.KafkaTopicsConfig;
import com.careconnect.laboratory.infrastructure.repository.LabOrderRepository;
import com.careconnect.laboratory.infrastructure.repository.LabReportRepository;
import com.careconnect.laboratory.infrastructure.repository.LabResultRepository;
import com.careconnect.laboratory.infrastructure.repository.TestAnalyteRepository;
import com.careconnect.laboratory.infrastructure.repository.TestCatalogueRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LaboratoryService {

    private static final Logger log = LoggerFactory.getLogger(LaboratoryService.class);

    private final LabOrderRepository orders;
    private final LabResultRepository results;
    private final LabReportRepository reports;
    private final TestCatalogueRepository catalogue;
    private final TestAnalyteRepository analytes;
    private final DomainEventPublisher events;
    private final Counter ordered;
    private final Counter criticalAlerts;

    public LaboratoryService(LabOrderRepository orders, LabResultRepository results,
                             LabReportRepository reports, TestCatalogueRepository catalogue,
                             TestAnalyteRepository analytes, DomainEventPublisher events,
                             MeterRegistry meters) {
        this.orders = orders;
        this.results = results;
        this.reports = reports;
        this.catalogue = catalogue;
        this.analytes = analytes;
        this.events = events;
        this.ordered = Counter.builder("careconnect.lab.orders").register(meters);
        this.criticalAlerts = Counter.builder("careconnect.lab.critical_alerts").register(meters);
    }

    @Transactional(readOnly = true)
    public List<TestCatalogue> catalogue(String query) {
        return catalogue.search(query);
    }

    /**
     * A doctor's order. Each test is priced at order time and the whole order is
     * immediately billable — `LabRequested` carries the charge to billing.
     */
    @Transactional
    public LabOrder order(CreateOrderRequest request, UUID doctorId,
                          String patientName, String doctorName) {
        String number = "LAB-%06d".formatted(orders.nextOrderNumber());
        LabOrder order = new LabOrder(number, request.encounterId(), request.patientId(),
                doctorId, patientName, doctorName, request.priority(),
                request.clinicalIndication());
        for (UUID testId : request.testIds()) {
            TestCatalogue test = catalogue.findById(testId)
                    .orElseThrow(() -> new LabException("Unknown test " + testId));
            order.addTest(test.getId(), test.getCode(), test.getName(), test.getPrice());
        }
        orders.save(order);
        ordered.increment();
        log.info("lab order {} created with {} tests for patient {}",
                number, order.getItems().size(), request.patientId());
        publishOrderEvent("LabRequested", order);
        return order;
    }

    @Transactional(readOnly = true)
    public List<LabOrder> worklist() {
        return orders.worklist();
    }

    @Transactional(readOnly = true)
    public LabOrder get(UUID id) {
        return orders.findById(id).orElseThrow(() -> new OrderNotFoundException(id));
    }

    /** Collection binds a barcoded sample — the accession number is generated here. */
    @Transactional
    public Sample collect(UUID orderId, String specimenType, String by) {
        LabOrder order = get(orderId);
        String accession = "A" + order.getOrderNumber().replace("LAB-", "") + "-"
                + Integer.toHexString((int) (System.nanoTime() & 0xFFFF)).toUpperCase();
        Sample sample = order.collectSample(accession, specimenType, by);
        publishOrderEvent("SampleCollected", order);
        return sample;
    }

    @Transactional
    public LabOrder beginProcessing(UUID orderId) {
        LabOrder order = get(orderId);
        order.beginProcessing();
        return order;
    }

    @Transactional
    public LabOrder reject(UUID orderId, String reason) {
        LabOrder order = get(orderId);
        order.reject(reason);
        log.info("lab order {} rejected: {}", order.getOrderNumber(), reason);
        publishOrderEvent("SampleRejected", order);
        return order;
    }

    /**
     * Result entry. Each value is classified against its analyte's reference and
     * critical ranges. A CRITICAL value fires an immediate alert to the ordering
     * doctor — before verification — because that is exactly the case where delay
     * is dangerous.
     */
    @Transactional
    public LabOrder enterResults(UUID orderId, EnterResultsRequest request) {
        LabOrder order = get(orderId);
        OrderItem item = order.getItems().stream()
                .filter(i -> i.getId().equals(request.orderItemId()))
                .findFirst()
                .orElseThrow(() -> new LabException("Test is not part of this order"));

        boolean anyCritical = false;
        for (var entry : request.results()) {
            TestAnalyte analyte = analytes.findById(entry.analyteId())
                    .orElseThrow(() -> new LabException("Unknown analyte"));
            BigDecimal numeric = parseNumeric(entry.value());
            ResultFlag flag = ResultFlag.classify(numeric, analyte.getRefLow(), analyte.getRefHigh(),
                    analyte.getCriticalLow(), analyte.getCriticalHigh());
            results.save(new LabResult(item.getId(), analyte.getId(), analyte.getName(),
                    entry.value(), analyte.getUnit(), analyte.getRefLow(), analyte.getRefHigh(), flag));
            if (flag.isCritical()) {
                anyCritical = true;
            }
        }
        if (order.getStatus() == OrderStatus.COLLECTED) {
            order.beginProcessing();
        }
        order.markReported();

        if (anyCritical) {
            criticalAlerts.increment();
            log.warn("CRITICAL result on order {} — alerting doctor {}",
                    order.getOrderNumber(), order.getDoctorId());
            publishOrderEvent("LabResultCritical", order);
        }
        return order;
    }

    /**
     * Verification releases the report to the patient. Only now does the patient
     * become able to see results; the doctor could already see reported values.
     */
    @Transactional
    public LabOrder verify(UUID orderId, String verifiedBy) {
        LabOrder order = get(orderId);
        order.verify();
        reports.save(new LabReport(order.getId(), null, verifiedBy));
        log.info("lab order {} verified and released", order.getOrderNumber());
        publishOrderEvent("ReportUploaded", order);
        return order;
    }

    // ---- reads with results ------------------------------------------------

    @Transactional(readOnly = true)
    public OrderResponse detail(UUID orderId) {
        LabOrder order = get(orderId);
        return OrderResponse.of(order, itemsWithResults(order));
    }

    @Transactional(readOnly = true)
    public Page<LabOrder> forPatient(UUID patientId, boolean releasedOnly, Pageable pageable) {
        return releasedOnly
                ? orders.findByPatientIdAndStatusOrderByOrderedAtDesc(patientId, OrderStatus.VERIFIED, pageable)
                : orders.findByPatientIdOrderByOrderedAtDesc(patientId, pageable);
    }

    private List<OrderItemResponse> itemsWithResults(LabOrder order) {
        List<UUID> itemIds = order.getItems().stream().map(OrderItem::getId).toList();
        Map<UUID, List<ResultResponse>> byItem = new HashMap<>();
        if (!itemIds.isEmpty()) {
            for (LabResult r : results.findByOrderItemIdIn(itemIds)) {
                byItem.computeIfAbsent(r.getOrderItemId(), k -> new ArrayList<>())
                        .add(ResultResponse.from(r));
            }
        }
        return order.getItems().stream()
                .map(i -> new OrderItemResponse(i.getId(), i.getTestCode(), i.getTestName(),
                        i.getPriceSnapshot(), byItem.getOrDefault(i.getId(), List.of())))
                .toList();
    }

    private BigDecimal parseNumeric(String value) {
        try {
            return new BigDecimal(value.trim());
        } catch (NumberFormatException e) {
            return null;   // qualitative result (e.g. "Positive") — no range classification
        }
    }

    private void publishOrderEvent(String type, LabOrder order) {
        events.publish(KafkaTopicsConfig.LAB_EVENTS, type, order.getId(), Map.of(
                "orderId", order.getId().toString(),
                "orderNumber", order.getOrderNumber(),
                "encounterId", order.getEncounterId() == null ? "" : order.getEncounterId().toString(),
                "patientId", order.getPatientId().toString(),
                "doctorId", order.getDoctorId().toString(),
                "patientName", order.getPatientName(),
                "doctorName", order.getDoctorName(),
                "amount", order.totalPrice().toPlainString(),
                "status", order.getStatus().name()));
    }
}
