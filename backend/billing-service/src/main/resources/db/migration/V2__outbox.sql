-- Transactional outbox (ADR-009). Events are written in the SAME transaction
-- as the business change; a relay publishes them to Kafka afterwards. This
-- closes the publish-after-commit gap: a crash between commit and publish no
-- longer loses the event — the row is already durable and gets picked up.
CREATE TABLE outbox_events (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    topic          VARCHAR(100) NOT NULL,
    event_type     VARCHAR(60)  NOT NULL,
    aggregate_id   UUID NOT NULL,
    payload        TEXT NOT NULL,          -- the full event envelope, ready to send
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    published_at   TIMESTAMPTZ,            -- null = still pending
    attempts       SMALLINT NOT NULL DEFAULT 0,
    last_error     VARCHAR(500)
);

-- The relay's hot query: oldest unpublished first.
CREATE INDEX idx_outbox_pending ON outbox_events (created_at) WHERE published_at IS NULL;
