-- Provider context schema

CREATE TABLE departments (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name        VARCHAR(120) NOT NULL UNIQUE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE doctors (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id           UUID UNIQUE,
    first_name        VARCHAR(80) NOT NULL,
    last_name         VARCHAR(80) NOT NULL,
    specialty         VARCHAR(120) NOT NULL,
    department_id     UUID NOT NULL REFERENCES departments (id),
    consultation_fee  NUMERIC(10,2) NOT NULL CHECK (consultation_fee >= 0),
    email             VARCHAR(255),
    phone             VARCHAR(25),
    status            VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by        VARCHAR(64),
    updated_by        VARCHAR(64),
    CONSTRAINT chk_doctors_status CHECK (status IN ('ACTIVE', 'INACTIVE'))
);

CREATE INDEX idx_doctors_specialty ON doctors (lower(specialty));

CREATE TABLE availability_slots (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    doctor_id     UUID NOT NULL REFERENCES doctors (id) ON DELETE CASCADE,
    day_of_week   SMALLINT NOT NULL CHECK (day_of_week BETWEEN 1 AND 7),  -- ISO: 1=Mon
    start_time    TIME NOT NULL,
    end_time      TIME NOT NULL,
    slot_minutes  SMALLINT NOT NULL DEFAULT 30 CHECK (slot_minutes BETWEEN 5 AND 240),
    CONSTRAINT chk_slot_times CHECK (start_time < end_time)
);

CREATE INDEX idx_slots_doctor ON availability_slots (doctor_id, day_of_week);

CREATE TABLE schedule_exceptions (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    doctor_id       UUID NOT NULL REFERENCES doctors (id) ON DELETE CASCADE,
    exception_date  DATE NOT NULL,
    reason          VARCHAR(200),
    UNIQUE (doctor_id, exception_date)
);

INSERT INTO departments (name) VALUES
    ('General Medicine'), ('Cardiology'), ('Pediatrics'), ('Orthopedics'), ('Dermatology');
