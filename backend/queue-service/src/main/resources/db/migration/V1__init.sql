-- Live Care Flow: the real-time OPD queue.
--
-- This is the operational heart of an outpatient department: patients check
-- in, receive a token, wait, get called, are seen, and leave. Everything the
-- rest of the system does downstream (charts, invoices, notifications) is
-- triggered by a consultation ENDING here.

CREATE TABLE queue_entries (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    appointment_id     UUID UNIQUE,                 -- null for walk-ins
    patient_id         UUID NOT NULL,
    doctor_id          UUID NOT NULL,
    patient_name       VARCHAR(160) NOT NULL,
    doctor_name        VARCHAR(160) NOT NULL,
    token_number       VARCHAR(12)  NOT NULL,       -- human-facing, e.g. "A-014"
    queue_date         DATE NOT NULL,
    priority           VARCHAR(20) NOT NULL DEFAULT 'NORMAL',
    status             VARCHAR(20) NOT NULL DEFAULT 'WAITING',
    complaint          VARCHAR(300),
    checked_in_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    called_at          TIMESTAMPTZ,
    started_at         TIMESTAMPTZ,
    completed_at       TIMESTAMPTZ,
    consultation_secs  INTEGER,                     -- feeds the wait-time model
    call_attempts      SMALLINT NOT NULL DEFAULT 0,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by         VARCHAR(64),
    updated_by         VARCHAR(64),
    CONSTRAINT chk_queue_priority CHECK (priority IN ('EMERGENCY','URGENT','NORMAL')),
    CONSTRAINT chk_queue_status CHECK
        (status IN ('WAITING','CALLED','IN_CONSULTATION','COMPLETED','SKIPPED','LEFT')),
    CONSTRAINT uq_token_per_doctor_day UNIQUE (doctor_id, queue_date, token_number)
);

-- The hot query: today's live queue for one doctor, ordered for calling.
CREATE INDEX idx_queue_live ON queue_entries (doctor_id, queue_date, status);
CREATE INDEX idx_queue_patient ON queue_entries (patient_id, queue_date DESC);

-- Per doctor/day token counter, so tokens are sequential and gap-free.
CREATE TABLE token_counters (
    doctor_id    UUID NOT NULL,
    queue_date   DATE NOT NULL,
    last_number  INTEGER NOT NULL DEFAULT 0,
    PRIMARY KEY (doctor_id, queue_date)
);

CREATE TABLE processed_events (
    event_id      VARCHAR(64) PRIMARY KEY,
    topic         VARCHAR(100) NOT NULL,
    processed_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
