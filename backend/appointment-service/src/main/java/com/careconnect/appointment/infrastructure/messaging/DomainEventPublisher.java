package com.careconnect.appointment.infrastructure.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

/**
 * Writes domain events to the OUTBOX inside the caller's transaction
 * (ADR-009). Nothing touches Kafka here: if the business transaction rolls
 * back, the event vanishes with it; if it commits, the event is durable and
 * {@link OutboxRelay} will deliver it. That atomicity is the whole point —
 * "save then publish" can lose events, and "publish then save" can invent them.
 */
@Component
public class DomainEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(DomainEventPublisher.class);

    private final OutboxRepository outbox;
    private final ObjectMapper objectMapper;

    public DomainEventPublisher(OutboxRepository outbox, ObjectMapper objectMapper) {
        this.outbox = outbox;
        this.objectMapper = objectMapper;
    }

    public void publish(String topic, String eventType, UUID aggregateId,
                        Map<String, Object> payload) {
        try {
            String correlationId = MDC.get("correlationId");
            String envelope = objectMapper.writeValueAsString(Map.of(
                    "eventId", UUID.randomUUID().toString(),
                    "eventType", eventType,
                    "occurredAt", Instant.now().toString(),
                    "aggregateId", aggregateId.toString(),
                    "version", 1,
                    "correlationId", correlationId == null ? "" : correlationId,
                    "payload", payload));
            outbox.save(new OutboxEvent(topic, eventType, aggregateId, envelope));
        } catch (Exception e) {
            // Serialization failure is a programming error: fail the business
            // transaction rather than silently dropping a domain event.
            log.error("event serialization failed type={} aggregate={}", eventType, aggregateId, e);
            throw new IllegalStateException("Could not serialize domain event " + eventType, e);
        }
    }
}
