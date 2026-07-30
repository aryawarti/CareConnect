package com.careconnect.queue.infrastructure.messaging;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface OutboxRepository extends JpaRepository<OutboxEvent, UUID> {

    /**
     * Oldest pending events, row-locked and skipping rows another relay
     * instance already holds — so running two replicas doesn't double-publish.
     *
     * Events that have exhausted their attempts are excluded rather than retried
     * forever. Without that filter one unpublishable payload sits at the head of
     * `order by created_at` and blocks every later event indefinitely: the
     * outbox stops being a queue and becomes a wall. Attempts are only counted
     * for failures caused by the record itself — see OutboxRelay.
     */
    @Query(value = """
            select * from outbox_events
            where published_at is null
              and attempts < :maxAttempts
            order by created_at
            limit :max
            for update skip locked
            """, nativeQuery = true)
    List<OutboxEvent> lockPending(int maxAttempts, int max);

    long countByPublishedAtIsNull();

    /** Given up on: unpublished and out of attempts. Exposed as a gauge. */
    long countByPublishedAtIsNullAndAttemptsGreaterThanEqual(short maxAttempts);
}
