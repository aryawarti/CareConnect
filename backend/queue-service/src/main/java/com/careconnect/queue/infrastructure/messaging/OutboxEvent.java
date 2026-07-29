package com.careconnect.queue.infrastructure.messaging;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** A domain event awaiting publication (ADR-009). */
@Entity
@Table(name = "outbox_events")
public class OutboxEvent {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(nullable = false)
    private String topic;

    @Column(name = "event_type", nullable = false)
    private String eventType;

    @Column(name = "aggregate_id", nullable = false)
    private UUID aggregateId;

    @Column(nullable = false, columnDefinition = "text")
    private String payload;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(nullable = false)
    private short attempts;

    @Column(name = "last_error", length = 500)
    private String lastError;

    protected OutboxEvent() { }

    public OutboxEvent(String topic, String eventType, UUID aggregateId, String payload) {
        this.topic = topic;
        this.eventType = eventType;
        this.aggregateId = aggregateId;
        this.payload = payload;
    }

    public void markPublished() {
        this.publishedAt = Instant.now();
    }

    public void recordFailure(String error) {
        this.attempts++;
        this.lastError = error == null ? null
                : error.substring(0, Math.min(error.length(), 500));
    }

    public UUID getId() { return id; }
    public String getTopic() { return topic; }
    public String getEventType() { return eventType; }
    public UUID getAggregateId() { return aggregateId; }
    public String getPayload() { return payload; }
    public Instant getPublishedAt() { return publishedAt; }
    public short getAttempts() { return attempts; }
}
