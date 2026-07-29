-- When a lab report is released, medical-record keeps a lightweight link so the
-- result appears in the patient's chart. The authoritative result data lives in
-- laboratory-service; this is a reference plus a display summary.
CREATE TABLE encounter_lab_reports (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    encounter_id  UUID REFERENCES encounters (id) ON DELETE CASCADE,
    patient_id    UUID NOT NULL,
    order_id      UUID NOT NULL,
    order_number  VARCHAR(20) NOT NULL,
    released_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (order_id)
);
CREATE INDEX idx_enc_lab_encounter ON encounter_lab_reports (encounter_id);
CREATE INDEX idx_enc_lab_patient ON encounter_lab_reports (patient_id);
