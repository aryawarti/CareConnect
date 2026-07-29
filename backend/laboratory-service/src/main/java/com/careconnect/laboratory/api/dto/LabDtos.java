package com.careconnect.laboratory.api.dto;

import com.careconnect.laboratory.domain.LabOrder;
import com.careconnect.laboratory.domain.LabPriority;
import com.careconnect.laboratory.domain.LabResult;
import com.careconnect.laboratory.domain.OrderItem;
import com.careconnect.laboratory.domain.Sample;
import com.careconnect.laboratory.domain.TestAnalyte;
import com.careconnect.laboratory.domain.TestCatalogue;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class LabDtos {
    private LabDtos() { }

    // ---- catalogue ----
    public record AnalyteResponse(UUID id, String name, String unit,
                                  BigDecimal refLow, BigDecimal refHigh) {
        static AnalyteResponse from(TestAnalyte a) {
            return new AnalyteResponse(a.getId(), a.getName(), a.getUnit(), a.getRefLow(), a.getRefHigh());
        }
    }
    public record CatalogueResponse(UUID id, String code, String name, String specimenType,
                                    String department, BigDecimal price, int tatMinutes,
                                    List<AnalyteResponse> analytes) {
        public static CatalogueResponse from(TestCatalogue t) {
            return new CatalogueResponse(t.getId(), t.getCode(), t.getName(), t.getSpecimenType(),
                    t.getDepartment(), t.getPrice(), t.getTatMinutes(),
                    t.getAnalytes().stream().map(AnalyteResponse::from).toList());
        }
    }

    // ---- ordering (doctor) ----
    public record CreateOrderRequest(
            UUID encounterId,
            @NotNull UUID patientId,
            @Size(max = 300) String clinicalIndication,
            LabPriority priority,
            @NotEmpty List<UUID> testIds) {
    }

    // ---- collection (technician) ----
    public record CollectRequest(@NotNull String specimenType) { }

    public record RejectRequest(@Size(max = 200) String reason) { }

    // ---- result entry (technician) ----
    public record ResultEntry(@NotNull UUID analyteId, @NotNull String value) { }
    public record EnterResultsRequest(@NotNull UUID orderItemId, @NotEmpty List<ResultEntry> results) { }

    // ---- responses ----
    public record ResultResponse(String analyteName, String value, String unit,
                                 BigDecimal refLow, BigDecimal refHigh, String flag) {
        public static ResultResponse from(LabResult r) {
            return new ResultResponse(r.getAnalyteName(), r.getValue(), r.getUnit(),
                    r.getRefLow(), r.getRefHigh(), r.getFlag() == null ? null : r.getFlag().name());
        }
    }
    public record OrderItemResponse(UUID id, String testCode, String testName,
                                    BigDecimal price, List<ResultResponse> results) { }
    public record SampleResponse(String accessionNo, String specimenType, Instant collectedAt) {
        static SampleResponse from(Sample s) {
            return new SampleResponse(s.getAccessionNo(), s.getSpecimenType(), s.getCollectedAt());
        }
    }
    public record OrderResponse(
            UUID id, String orderNumber, UUID encounterId, UUID patientId, UUID doctorId,
            String patientName, String doctorName, String priority, String status,
            String clinicalIndication, Instant orderedAt, BigDecimal total,
            List<OrderItemResponse> items, List<SampleResponse> samples) {

        public static OrderResponse of(LabOrder o, List<OrderItemResponse> items) {
            return new OrderResponse(o.getId(), o.getOrderNumber(), o.getEncounterId(),
                    o.getPatientId(), o.getDoctorId(), o.getPatientName(), o.getDoctorName(),
                    o.getPriority().name(), o.getStatus().name(), o.getClinicalIndication(),
                    o.getOrderedAt(), o.totalPrice(), items,
                    o.getSamples().stream().map(SampleResponse::from).toList());
        }

        /** Summary for lists — no results, no samples. */
        public static OrderResponse summary(LabOrder o) {
            return new OrderResponse(o.getId(), o.getOrderNumber(), o.getEncounterId(),
                    o.getPatientId(), o.getDoctorId(), o.getPatientName(), o.getDoctorName(),
                    o.getPriority().name(), o.getStatus().name(), o.getClinicalIndication(),
                    o.getOrderedAt(), o.totalPrice(),
                    o.getItems().stream().map(i -> new OrderItemResponse(
                            i.getId(), i.getTestCode(), i.getTestName(), i.getPriceSnapshot(), List.of())).toList(),
                    o.getSamples().stream().map(SampleResponse::from).toList());
        }
    }
}
