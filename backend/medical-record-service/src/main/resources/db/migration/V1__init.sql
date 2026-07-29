-- Clinical Records schema. Append-oriented: notes are amended, never
-- destructively edited; signed encounters are immutable (FR-E2).

CREATE TABLE encounters (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    appointment_id  UUID NOT NULL UNIQUE,        -- ref appointment-service; also the idempotency key
    patient_id      UUID NOT NULL,
    doctor_id       UUID NOT NULL,
    patient_name    VARCHAR(160) NOT NULL,       -- snapshot from the event
    doctor_name     VARCHAR(160) NOT NULL,
    occurred_at     TIMESTAMPTZ NOT NULL,        -- when the visit happened
    chief_complaint VARCHAR(500),
    notes           TEXT,
    status          VARCHAR(20) NOT NULL DEFAULT 'OPEN',
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by      VARCHAR(64),
    updated_by      VARCHAR(64),
    CONSTRAINT chk_encounter_status CHECK (status IN ('OPEN', 'SIGNED', 'AMENDED'))
);

CREATE INDEX idx_encounters_patient ON encounters (patient_id, occurred_at DESC);
CREATE INDEX idx_encounters_doctor ON encounters (doctor_id, occurred_at DESC);

CREATE TABLE diagnoses (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    encounter_id  UUID NOT NULL REFERENCES encounters (id) ON DELETE CASCADE,
    code          VARCHAR(20) NOT NULL,          -- ICD-10 style, e.g. J06.9
    description   VARCHAR(300) NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE prescriptions (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    encounter_id   UUID NOT NULL REFERENCES encounters (id) ON DELETE CASCADE,
    medication     VARCHAR(200) NOT NULL,
    dosage         VARCHAR(100) NOT NULL,
    frequency      VARCHAR(100) NOT NULL,
    duration_days  SMALLINT NOT NULL CHECK (duration_days BETWEEN 1 AND 365),
    instructions   VARCHAR(500),
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_diagnoses_encounter ON diagnoses (encounter_id);
CREATE INDEX idx_prescriptions_encounter ON prescriptions (encounter_id);

-- Amendments after signing are recorded, never overwritten (audit trail).
CREATE TABLE encounter_amendments (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    encounter_id  UUID NOT NULL REFERENCES encounters (id) ON DELETE CASCADE,
    previous_note TEXT,
    reason        VARCHAR(500) NOT NULL,
    amended_by    VARCHAR(64),
    amended_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE processed_events (
    event_id      VARCHAR(64) PRIMARY KEY,
    topic         VARCHAR(100) NOT NULL,
    processed_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
