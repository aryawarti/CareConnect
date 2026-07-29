package com.careconnect.notification.infrastructure.messaging;

import com.careconnect.notification.application.NotificationComposer;
import com.careconnect.notification.domain.ProcessedEvent;
import com.careconnect.notification.infrastructure.repository.NotificationRepository;
import com.careconnect.notification.infrastructure.repository.ProcessedEventRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * The consumer side of ADR-004. Guarantees:
 *  - idempotency: eventId recorded in the SAME transaction as the side effect;
 *    a redelivered event is a no-op (Kafka is at-least-once by contract)
 *  - poison messages: after retries (see KafkaErrorConfig) land in <topic>.DLT
 *  - "delivery" in dev = structured log line + notifications row
 */
@Component
public class EventConsumer {

    private static final Logger log = LoggerFactory.getLogger(EventConsumer.class);

    private final ProcessedEventRepository processed;
    private final NotificationRepository notifications;
    private final ObjectMapper objectMapper;

    public EventConsumer(ProcessedEventRepository processed,
                         NotificationRepository notifications, ObjectMapper objectMapper) {
        this.processed = processed;
        this.notifications = notifications;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = {"appointment.events", "patient.events", "billing.events", "lab.events"},
                   groupId = "notification-service")
    @Transactional
    public void onEvent(String message) throws Exception {
        Map<String, Object> envelope = objectMapper.readValue(message, new TypeReference<>() { });
        String eventId = (String) envelope.get("eventId");
        String eventType = (String) envelope.get("eventType");

        if (eventId == null || processed.existsById(eventId)) {
            log.debug("duplicate or unidentified event skipped id={}", eventId);
            return;
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> payload = (Map<String, Object>) envelope.getOrDefault("payload", Map.of());

        NotificationComposer.compose(eventType, eventId, payload).ifPresent(notification -> {
            notifications.save(notification);
            // Dev delivery: the log IS the email (SMTP is a config change away).
            log.info("EMAIL to={} subject=\"{}\" body=\"{}\"",
                    notification.getRecipientRef(), notification.getSubject(), notification.getBody());
        });
        processed.save(new ProcessedEvent(eventId, eventType));
    }
}
