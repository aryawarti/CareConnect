package com.careconnect.appointment.infrastructure.messaging;

import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface OutboxRepository extends JpaRepository<OutboxEvent, UUID> {

    /**
     * Oldest pending events, row-locked and skipping rows another relay
     * instance already holds — so running two replicas doesn't double-publish.
     */
    @Query(value = """
            select * from outbox_events
            where published_at is null
            order by created_at
            limit :max
            for update skip locked
            """, nativeQuery = true)
    List<OutboxEvent> lockPending(int max);

    long countByPublishedAtIsNull();

    List<OutboxEvent> findByPublishedAtIsNullOrderByCreatedAt(Limit limit);
}
