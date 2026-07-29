package com.careconnect.patient.infrastructure.messaging;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Publishes outbox rows to Kafka and marks them published, in one transaction.
 *
 * Delivery semantics: at-least-once. A crash after the Kafka send but before
 * the commit re-sends the event — which is exactly why every consumer is
 * idempotent (processed_events). Exactly-once would require Kafka
 * transactions spanning two systems; at-least-once + idempotent consumers is
 * the simpler and more common production answer.
 */
@Component
public class OutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);

    private final OutboxRepository outbox;
    private final KafkaTemplate<String, String> kafka;
    private final Counter published;
    private final Counter failed;
    private final int batchSize;

    public OutboxRelay(OutboxRepository outbox, KafkaTemplate<String, String> kafka,
                       MeterRegistry meters,
                       @Value("${careconnect.outbox.batch-size:100}") int batchSize) {
        this.outbox = outbox;
        this.kafka = kafka;
        this.batchSize = batchSize;
        this.published = Counter.builder("careconnect.outbox.published")
                .description("Domain events successfully relayed to Kafka").register(meters);
        this.failed = Counter.builder("careconnect.outbox.failed")
                .description("Outbox publish attempts that failed").register(meters);
        meters.gauge("careconnect.outbox.pending", outbox, OutboxRepository::countByPublishedAtIsNull);
    }

    @Scheduled(fixedDelayString = "${careconnect.outbox.poll-interval-ms:1000}")
    @Transactional
    public void relay() {
        List<OutboxEvent> pending = outbox.lockPending(batchSize);
        for (OutboxEvent event : pending) {
            try {
                kafka.send(event.getTopic(), event.getAggregateId().toString(), event.getPayload())
                        .get();   // synchronous: only mark published on real success
                event.markPublished();
                published.increment();
            } catch (Exception e) {
                event.recordFailure(e.getMessage());
                failed.increment();
                log.warn("outbox publish failed id={} attempts={}", event.getId(),
                        event.getAttempts());
                Thread.currentThread().interrupt();
                break;   // broker is unhappy — stop this batch, retry next tick
            }
        }
        if (!pending.isEmpty()) {
            log.debug("relayed {} outbox events", pending.size());
        }
    }
}
