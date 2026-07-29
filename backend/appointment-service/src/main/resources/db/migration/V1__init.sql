-- Scheduling context schema. btree_gist enables the exclusion constraint that
-- makes double-booking impossible at the database level (last line of defense
-- behind the application-level check — check-then-act alone is a race).
CREATE EXTENSION IF NOT EXISTS btree_gist;

CREATE TABLE appointments (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    patient_id    UUID NOT NULL,               -- ref patient-service (UUID only, ADR-003)
    doctor_id     UUID NOT NULL,               -- ref provider-service
    start_at      TIMESTAMPTZ NOT NULL,
    end_at        TIMESTAMPTZ NOT NULL,
    status        VARCHAR(20) NOT NULL DEFAULT 'REQUESTED',
    reason        VARCHAR(500),
    fee_snapshot  NUMERIC(10,2) NOT NULL,      -- fee at booking time; invoices must not drift
    patient_name  VARCHAR(160) NOT NULL,       -- denormalized display snapshot
    doctor_name   VARCHAR(160) NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by    VARCHAR(64),
    updated_by    VARCHAR(64),
    CONSTRAINT chk_appointment_status CHECK
        (status IN ('REQUESTED','CONFIRMED','COMPLETED','CANCELLED','NO_SHOW')),
    CONSTRAINT chk_appointment_times CHECK (start_at < end_at),
    CONSTRAINT no_double_booking EXCLUDE USING gist
        (doctor_id WITH =, tstzrange(start_at, end_at) WITH &&)
        WHERE (status IN ('REQUESTED','CONFIRMED'))
);

CREATE INDEX idx_appointments_patient ON appointments (patient_id, start_at DESC);
CREATE INDEX idx_appointments_doctor_day ON appointments (doctor_id, start_at);

CREATE TABLE appointment_status_history (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    appointment_id  UUID NOT NULL REFERENCES appointments (id) ON DELETE CASCADE,
    from_status     VARCHAR(20),
    to_status       VARCHAR(20) NOT NULL,
    changed_by      VARCHAR(64),
    changed_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_history_appointment ON appointment_status_history (appointment_id);
