-- Billing schema. Money: exact numerics only (never floating point), and
-- every state change is auditable.

CREATE SEQUENCE invoice_number_seq START WITH 5001;

CREATE TABLE invoices (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    invoice_number  VARCHAR(20) NOT NULL UNIQUE,
    appointment_id  UUID NOT NULL UNIQUE,     -- one visit, one invoice (idempotency)
    patient_id      UUID NOT NULL,
    doctor_id       UUID NOT NULL,
    patient_name    VARCHAR(160) NOT NULL,    -- snapshots: an invoice must not
    doctor_name     VARCHAR(160) NOT NULL,    -- change when profiles change
    amount          NUMERIC(10,2) NOT NULL CHECK (amount >= 0),
    status          VARCHAR(20) NOT NULL DEFAULT 'ISSUED',
    issued_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    paid_at         TIMESTAMPTZ,
    voided_reason   VARCHAR(300),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by      VARCHAR(64),
    updated_by      VARCHAR(64),
    CONSTRAINT chk_invoice_status CHECK (status IN ('ISSUED', 'PAID', 'VOID'))
);

CREATE INDEX idx_invoices_patient ON invoices (patient_id, issued_at DESC);
CREATE INDEX idx_invoices_status ON invoices (status);

CREATE TABLE payments (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    invoice_id   UUID NOT NULL REFERENCES invoices (id) ON DELETE CASCADE,
    amount       NUMERIC(10,2) NOT NULL CHECK (amount > 0),
    method       VARCHAR(20) NOT NULL DEFAULT 'SIMULATED',
    reference    VARCHAR(64) NOT NULL UNIQUE,   -- payment idempotency key
    paid_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    recorded_by  VARCHAR(64)
);

CREATE INDEX idx_payments_invoice ON payments (invoice_id);

CREATE TABLE processed_events (
    event_id      VARCHAR(64) PRIMARY KEY,
    topic         VARCHAR(100) NOT NULL,
    processed_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
