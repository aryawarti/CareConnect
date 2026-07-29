package com.careconnect.billing.infrastructure.messaging;

import com.careconnect.billing.application.BillingService;
import com.careconnect.billing.domain.ProcessedEvent;
import com.careconnect.billing.infrastructure.repository.ProcessedEventRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Lab orders are billable the moment they are placed. Billing consumes
 * `LabRequested` and raises a charge — the same pattern as consultation
 * billing, so every clinical service that costs money produces a bill line
 * without anyone remembering to create one (BR-BIL-1).
 */
@Component
public class LabEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(LabEventConsumer.class);

    private final BillingService billing;
    private final ProcessedEventRepository processed;
    private final ObjectMapper objectMapper;

    public LabEventConsumer(BillingService billing, ProcessedEventRepository processed,
                            ObjectMapper objectMapper) {
        this.billing = billing;
        this.processed = processed;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = "lab.events", groupId = "billing-service")
    @Transactional
    public void onLabEvent(String message) throws Exception {
        Map<String, Object> envelope = objectMapper.readValue(message, new TypeReference<>() { });
        String eventId = (String) envelope.get("eventId");
        String eventType = (String) envelope.get("eventType");
        if (eventId == null || processed.existsById(eventId)) {
            return;
        }
        if ("LabRequested".equals(eventType)) {
            @SuppressWarnings("unchecked")
            Map<String, Object> p = (Map<String, Object>) envelope.getOrDefault("payload", Map.of());
            BigDecimal amount = new BigDecimal((String) p.get("amount"));
            if (amount.signum() > 0) {
                billing.issueForService(
                        "LAB", (String) p.get("orderNumber"),
                        UUID.fromString((String) p.get("patientId")),
                        UUID.fromString((String) p.get("doctorId")),
                        (String) p.get("patientName"), (String) p.get("doctorName"),
                        amount, "Laboratory: " + p.get("orderNumber"));
                log.info("lab charge {} raised for patient {}", p.get("orderNumber"), p.get("patientId"));
            }
        }
        processed.save(new ProcessedEvent(eventId, "lab.events"));
    }
}
