-- appointment-service becomes an event CONSUMER in the Live Care Flow phase
-- (queue.events -> complete the appointment), so it needs its own idempotency
-- ledger. Same pattern as every other consumer in the system.
CREATE TABLE processed_events (
    event_id      VARCHAR(64) PRIMARY KEY,
    topic         VARCHAR(100) NOT NULL,
    processed_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
