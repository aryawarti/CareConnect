package com.careconnect.queue.infrastructure.messaging;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.List;
import java.util.concurrent.ExecutionException;
import org.apache.kafka.common.errors.RetriableException;
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
 *
 * Failure policy, and the distinction is the whole point:
 *
 *  - **The broker is unavailable** (anything Kafka marks {@link RetriableException}:
 *    timeouts, network errors, no leader). Stop the batch and retry everything
 *    next tick *without* counting an attempt. Counting here would let a long
 *    outage burn through the retry budget and abandon events that were never
 *    faulty — which would defeat the entire purpose of having an outbox.
 *  - **This record cannot be published** (payload too large, topic rejected,
 *    serialization failure). Count the attempt and move to the next event. One
 *    poison record must not block everything queued behind it, and because
 *    `lockPending` orders by created_at, that is precisely what would happen.
 *
 * After `max-attempts`, an event is left unpublished and excluded from further
 * polling. It stays in `outbox_events` with its `last_error` — the table doubles
 * as the dead-letter store, so nothing is ever silently discarded — and the
 * `careconnect.outbox.dead` gauge goes non-zero, which is the signal a human
 * needs to look.
 */
@Component
public class OutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);

    private final OutboxRepository outbox;
    private final KafkaTemplate<String, String> kafka;
    private final Counter published;
    private final Counter failed;
    private final Counter abandoned;
    private final int batchSize;
    private final short maxAttempts;

    public OutboxRelay(OutboxRepository outbox, KafkaTemplate<String, String> kafka,
                       MeterRegistry meters,
                       @Value("${careconnect.outbox.batch-size:100}") int batchSize,
                       @Value("${careconnect.outbox.max-attempts:10}") short maxAttempts) {
        this.outbox = outbox;
        this.kafka = kafka;
        this.batchSize = batchSize;
        this.maxAttempts = maxAttempts;
        this.published = Counter.builder("careconnect.outbox.published")
                .description("Domain events successfully relayed to Kafka").register(meters);
        this.failed = Counter.builder("careconnect.outbox.failed")
                .description("Outbox publish attempts that failed").register(meters);
        this.abandoned = Counter.builder("careconnect.outbox.abandoned")
                .description("Events given up on after exhausting their attempts").register(meters);
        meters.gauge("careconnect.outbox.pending", outbox, OutboxRepository::countByPublishedAtIsNull);
        // Should sit flat at zero. Anything else means domain events are not
        // reaching their consumers, so it is a gauge to alert on rather than a
        // log line to discover later.
        meters.gauge("careconnect.outbox.dead", outbox,
                repo -> repo.countByPublishedAtIsNullAndAttemptsGreaterThanEqual(maxAttempts));
    }

    @Scheduled(fixedDelayString = "${careconnect.outbox.poll-interval-ms:1000}")
    @Transactional
    public void relay() {
        List<OutboxEvent> pending = outbox.lockPending(maxAttempts, batchSize);
        int sent = 0;
        for (OutboxEvent event : pending) {
            try {
                kafka.send(event.getTopic(), event.getAggregateId().toString(), event.getPayload())
                        .get();   // synchronous: only mark published on real success
                event.markPublished();
                published.increment();
                sent++;
            } catch (InterruptedException e) {
                // A real interruption (graceful shutdown). Restore the flag for
                // whoever owns this thread and stop promptly; the remaining rows
                // are still pending and get picked up on the next start.
                Thread.currentThread().interrupt();
                log.info("outbox relay interrupted during shutdown, {} events left pending",
                        pending.size() - sent);
                break;
            } catch (Exception e) {
                failed.increment();
                Throwable cause = e instanceof ExecutionException && e.getCause() != null
                        ? e.getCause()
                        : e;
                if (cause instanceof RetriableException) {
                    log.warn("outbox publish deferred, broker not accepting writes: {}", cause);
                    break;
                }
                event.recordFailure(cause.toString());
                if (event.getAttempts() >= maxAttempts) {
                    abandoned.increment();
                    log.error("outbox event {} type={} abandoned after {} attempts; kept in "
                                    + "outbox_events for inspection, not retried again: {}",
                            event.getId(), event.getEventType(), event.getAttempts(), cause);
                } else {
                    log.warn("outbox publish failed id={} type={} attempts={}/{}: {}",
                            event.getId(), event.getEventType(), event.getAttempts(),
                            maxAttempts, cause);
                }
            }
        }
        if (sent > 0) {
            log.debug("relayed {} outbox events", sent);
        }
    }
}
