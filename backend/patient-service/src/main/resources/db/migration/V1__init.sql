-- Patient context schema (docs/architecture/database-design.md)

CREATE SEQUENCE patient_number_seq START WITH 100001;

CREATE TABLE patients (
    id                       UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id                  UUID UNIQUE,              -- identity-service userId; null until linked
    patient_number           VARCHAR(12) NOT NULL UNIQUE,
    first_name               VARCHAR(80)  NOT NULL,
    last_name                VARCHAR(80)  NOT NULL,
    date_of_birth            DATE         NOT NULL,
    gender                   VARCHAR(20)  NOT NULL,
    phone                    VARCHAR(25),
    email                    VARCHAR(255),
    address_line1            VARCHAR(160),
    address_line2            VARCHAR(160),
    city                     VARCHAR(80),
    state                    VARCHAR(80),
    postal_code              VARCHAR(16),
    emergency_contact_name   VARCHAR(160),
    emergency_contact_phone  VARCHAR(25),
    status                   VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at               TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at               TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by               VARCHAR(64),
    updated_by               VARCHAR(64),
    CONSTRAINT chk_patients_status CHECK (status IN ('ACTIVE', 'INACTIVE')),
    CONSTRAINT chk_patients_gender CHECK (gender IN ('MALE', 'FEMALE', 'OTHER', 'UNDISCLOSED'))
);

CREATE INDEX idx_patients_last_name ON patients (lower(last_name));
CREATE INDEX idx_patients_phone ON patients (phone);
