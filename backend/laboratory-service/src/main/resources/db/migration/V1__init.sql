-- Laboratory context. A test catalogue defines what can be ordered and how each
-- analyte is interpreted; orders come from clinical encounters; samples are
-- bound to patients only by barcode; results are flagged against reference
-- ranges and released only after verification.

CREATE TABLE test_catalogue (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code          VARCHAR(20) NOT NULL UNIQUE,
    name          VARCHAR(160) NOT NULL,
    specimen_type VARCHAR(40) NOT NULL,           -- BLOOD, URINE, SWAB, STOOL...
    department    VARCHAR(60) NOT NULL,           -- HAEMATOLOGY, BIOCHEMISTRY...
    price         NUMERIC(10,2) NOT NULL CHECK (price >= 0),
    tat_minutes   INTEGER NOT NULL DEFAULT 240,   -- expected turnaround
    active        BOOLEAN NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_catalogue_active ON test_catalogue (active);

CREATE TABLE test_analytes (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    test_id       UUID NOT NULL REFERENCES test_catalogue (id) ON DELETE CASCADE,
    name          VARCHAR(120) NOT NULL,
    unit          VARCHAR(30),
    ref_low       NUMERIC(12,4),
    ref_high      NUMERIC(12,4),
    critical_low  NUMERIC(12,4),                  -- below this = life-threatening
    critical_high NUMERIC(12,4),
    display_order SMALLINT NOT NULL DEFAULT 0
);
CREATE INDEX idx_analytes_test ON test_analytes (test_id);

CREATE TABLE lab_orders (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    order_number        VARCHAR(20) NOT NULL UNIQUE,
    encounter_id        UUID,                      -- ref medical-record; null for direct orders
    patient_id          UUID NOT NULL,
    doctor_id           UUID NOT NULL,
    patient_name        VARCHAR(160) NOT NULL,
    doctor_name         VARCHAR(160) NOT NULL,
    priority            VARCHAR(20) NOT NULL DEFAULT 'ROUTINE',
    status              VARCHAR(20) NOT NULL DEFAULT 'ORDERED',
    clinical_indication VARCHAR(300),
    ordered_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    branch_id           UUID,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by          VARCHAR(64),
    updated_by          VARCHAR(64),
    CONSTRAINT chk_lab_priority CHECK (priority IN ('ROUTINE','URGENT','STAT')),
    CONSTRAINT chk_lab_status CHECK
        (status IN ('ORDERED','COLLECTED','IN_PROCESS','REPORTED','VERIFIED','REJECTED','CANCELLED'))
);
CREATE INDEX idx_orders_worklist ON lab_orders (status, priority, ordered_at);
CREATE INDEX idx_orders_patient ON lab_orders (patient_id, ordered_at DESC);

CREATE TABLE lab_order_items (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id       UUID NOT NULL REFERENCES lab_orders (id) ON DELETE CASCADE,
    test_id        UUID NOT NULL,
    test_code      VARCHAR(20) NOT NULL,
    test_name      VARCHAR(160) NOT NULL,
    price_snapshot NUMERIC(10,2) NOT NULL,        -- price at order time; billing must not drift
    status         VARCHAR(20) NOT NULL DEFAULT 'ORDERED'
);
CREATE INDEX idx_items_order ON lab_order_items (order_id);

CREATE TABLE lab_samples (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id        UUID NOT NULL REFERENCES lab_orders (id) ON DELETE CASCADE,
    accession_no    VARCHAR(24) NOT NULL UNIQUE,   -- the barcode; sample identity
    specimen_type   VARCHAR(40) NOT NULL,
    collected_by    VARCHAR(64),
    collected_at    TIMESTAMPTZ,
    rejected_reason VARCHAR(200)
);

CREATE TABLE lab_results (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    order_item_id UUID NOT NULL REFERENCES lab_order_items (id) ON DELETE CASCADE,
    analyte_id    UUID NOT NULL,
    analyte_name  VARCHAR(120) NOT NULL,
    value         VARCHAR(60) NOT NULL,
    unit          VARCHAR(30),
    ref_low       NUMERIC(12,4),
    ref_high      NUMERIC(12,4),
    flag          VARCHAR(8),                      -- NORMAL, HIGH, LOW, CRITICAL
    entered_by    VARCHAR(64),
    entered_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_results_item ON lab_results (order_item_id);

CREATE TABLE lab_reports (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id     UUID NOT NULL UNIQUE REFERENCES lab_orders (id) ON DELETE CASCADE,
    file_key     VARCHAR(200),                    -- object-storage key (Planned wiring)
    released_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    released_by  VARCHAR(64)
);

CREATE SEQUENCE lab_order_seq START WITH 3001;

CREATE TABLE processed_events (
    event_id      VARCHAR(64) PRIMARY KEY,
    topic         VARCHAR(100) NOT NULL,
    processed_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Seed a starter catalogue so ordering works immediately.
INSERT INTO test_catalogue (code, name, specimen_type, department, price, tat_minutes) VALUES
    ('CBC',  'Complete Blood Count',      'BLOOD', 'HAEMATOLOGY',  400, 120),
    ('LFT',  'Liver Function Test',       'BLOOD', 'BIOCHEMISTRY', 800, 240),
    ('KFT',  'Kidney Function Test',      'BLOOD', 'BIOCHEMISTRY', 700, 240),
    ('LIPID','Lipid Profile',             'BLOOD', 'BIOCHEMISTRY', 600, 240),
    ('TSH',  'Thyroid Stimulating Hormone','BLOOD','ENDOCRINOLOGY', 350, 360),
    ('HBA1C','Glycated Haemoglobin',      'BLOOD', 'BIOCHEMISTRY', 500, 240),
    ('URINE','Urine Routine',             'URINE', 'PATHOLOGY',    200, 90),
    ('CRP',  'C-Reactive Protein',        'BLOOD', 'BIOCHEMISTRY', 450, 180);

-- A few analytes with reference and critical ranges for the demo tests.
INSERT INTO test_analytes (test_id, name, unit, ref_low, ref_high, critical_low, critical_high, display_order)
SELECT id, 'Haemoglobin', 'g/dL', 13.0, 17.0, 7.0, 20.0, 1 FROM test_catalogue WHERE code='CBC';
INSERT INTO test_analytes (test_id, name, unit, ref_low, ref_high, critical_low, critical_high, display_order)
SELECT id, 'WBC Count', '10^3/uL', 4.0, 11.0, 1.0, 30.0, 2 FROM test_catalogue WHERE code='CBC';
INSERT INTO test_analytes (test_id, name, unit, ref_low, ref_high, critical_low, critical_high, display_order)
SELECT id, 'Platelet Count', '10^3/uL', 150, 410, 20, 1000, 3 FROM test_catalogue WHERE code='CBC';
INSERT INTO test_analytes (test_id, name, unit, ref_low, ref_high, critical_low, critical_high, display_order)
SELECT id, 'Fasting Glucose', 'mg/dL', 70, 100, 40, 500, 1 FROM test_catalogue WHERE code='HBA1C';
INSERT INTO test_analytes (test_id, name, unit, ref_low, ref_high, critical_low, critical_high, display_order)
SELECT id, 'HbA1c', '%', 4.0, 5.7, NULL, NULL, 2 FROM test_catalogue WHERE code='HBA1C';
INSERT INTO test_analytes (test_id, name, unit, ref_low, ref_high, critical_low, critical_high, display_order)
SELECT id, 'Total Cholesterol', 'mg/dL', 125, 200, NULL, NULL, 1 FROM test_catalogue WHERE code='LIPID';
INSERT INTO test_analytes (test_id, name, unit, ref_low, ref_high, critical_low, critical_high, display_order)
SELECT id, 'Serum Creatinine', 'mg/dL', 0.7, 1.3, NULL, 10.0, 1 FROM test_catalogue WHERE code='KFT';
INSERT INTO test_analytes (test_id, name, unit, ref_low, ref_high, critical_low, critical_high, display_order)
SELECT id, 'TSH', 'mIU/L', 0.4, 4.0, NULL, NULL, 1 FROM test_catalogue WHERE code='TSH';
