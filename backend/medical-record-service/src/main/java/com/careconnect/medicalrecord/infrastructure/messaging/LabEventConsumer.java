package com.careconnect.medicalrecord.infrastructure.messaging;

import com.careconnect.medicalrecord.domain.EncounterLabReport;
import com.careconnect.medicalrecord.domain.ProcessedEvent;
import com.careconnect.medicalrecord.infrastructure.repository.EncounterLabReportRepository;
import com.careconnect.medicalrecord.infrastructure.repository.ProcessedEventRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * When a lab report is released (`ReportUploaded`), the result becomes part of
 * the patient's chart. We store a link (order id + number) rather than copying
 * the values — laboratory-service remains the source of truth; the chart just
 * knows the report exists and can fetch it.
 */
@Component
public class LabEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(LabEventConsumer.class);

    private final EncounterLabReportRepository links;
    private final ProcessedEventRepository processed;
    private final ObjectMapper objectMapper;

    public LabEventConsumer(EncounterLabReportRepository links,
                            ProcessedEventRepository processed, ObjectMapper objectMapper) {
        this.links = links;
        this.processed = processed;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = "lab.events", groupId = "medical-record-service")
    @Transactional
    public void onLabEvent(String message) throws Exception {
        Map<String, Object> envelope = objectMapper.readValue(message, new TypeReference<>() { });
        String eventId = (String) envelope.get("eventId");
        String eventType = (String) envelope.get("eventType");
        if (eventId == null || processed.existsById(eventId)) {
            return;
        }
        if ("ReportUploaded".equals(eventType)) {
            @SuppressWarnings("unchecked")
            Map<String, Object> p = (Map<String, Object>) envelope.getOrDefault("payload", Map.of());
            UUID orderId = UUID.fromString((String) p.get("orderId"));
            if (!links.existsByOrderId(orderId)) {
                String enc = (String) p.get("encounterId");
                links.save(new EncounterLabReport(
                        enc == null || enc.isBlank() ? null : UUID.fromString(enc),
                        UUID.fromString((String) p.get("patientId")),
                        orderId, (String) p.get("orderNumber")));
                log.info("lab report {} linked to chart for patient {}",
                        p.get("orderNumber"), p.get("patientId"));
            }
        }
        processed.save(new ProcessedEvent(eventId, "lab.events"));
    }
}
