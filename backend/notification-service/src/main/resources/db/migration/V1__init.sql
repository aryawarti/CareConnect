-- Notification context: delivery log + idempotency ledger.

CREATE TABLE notifications (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    recipient_ref      VARCHAR(160) NOT NULL,   -- patientId or email; resolution improves later
    channel            VARCHAR(20)  NOT NULL DEFAULT 'EMAIL',
    template_code      VARCHAR(60)  NOT NULL,
    subject            VARCHAR(200) NOT NULL,
    body               TEXT         NOT NULL,
    status             VARCHAR(20)  NOT NULL DEFAULT 'SENT',   -- dev delivery = log
    source_event_id    VARCHAR(64)  NOT NULL UNIQUE,           -- idempotency at the data level too
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- At-least-once delivery makes duplicates a certainty, not an edge case:
-- event ids are recorded in the SAME transaction as the side effect.
CREATE TABLE processed_events (
    event_id      VARCHAR(64) PRIMARY KEY,
    topic         VARCHAR(100) NOT NULL,
    processed_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
