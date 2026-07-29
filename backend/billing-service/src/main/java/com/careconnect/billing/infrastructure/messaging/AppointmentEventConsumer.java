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
 * appointment.events → invoices. Same double-idempotency as medical-record
 * (processed_events + unique appointment_id): a visit yields exactly one
 * invoice no matter how often the event is delivered or replayed.
 */
@Component
public class AppointmentEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(AppointmentEventConsumer.class);

    private final BillingService billing;
    private final ProcessedEventRepository processed;
    private final ObjectMapper objectMapper;

    public AppointmentEventConsumer(BillingService billing, ProcessedEventRepository processed,
                                    ObjectMapper objectMapper) {
        this.billing = billing;
        this.processed = processed;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = "appointment.events", groupId = "billing-service")
    @Transactional
    public void onAppointmentEvent(String message) throws Exception {
        Map<String, Object> envelope = objectMapper.readValue(message, new TypeReference<>() { });
        String eventId = (String) envelope.get("eventId");
        String eventType = (String) envelope.get("eventType");

        if (eventId == null || processed.existsById(eventId)) {
            return;
        }
        if ("AppointmentCompleted".equals(eventType)) {
            @SuppressWarnings("unchecked")
            Map<String, Object> payload = (Map<String, Object>) envelope.getOrDefault("payload", Map.of());
            billing.issueForCompletedAppointment(
                    UUID.fromString((String) payload.get("appointmentId")),
                    UUID.fromString((String) payload.get("patientId")),
                    UUID.fromString((String) payload.get("doctorId")),
                    (String) payload.get("patientName"),
                    (String) payload.get("doctorName"),
                    new BigDecimal((String) payload.get("fee")));
            log.info("invoice generated from event {}", eventId);
        }
        // NO_SHOW could carry a fee policy later; CANCELLED never bills.
        processed.save(new ProcessedEvent(eventId, "appointment.events"));
    }
}
