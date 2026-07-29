-- Doctors may now apply themselves (like patients register themselves), but a
-- hospital cannot let anyone claim to be a doctor: an applicant stays PENDING
-- and invisible to patients until an administrator verifies their credentials.
ALTER TABLE doctors
    ADD COLUMN verification VARCHAR(20) NOT NULL DEFAULT 'APPROVED',
    ADD COLUMN qualification VARCHAR(160),
    ADD COLUMN registration_no VARCHAR(60),
    ADD COLUMN experience_years SMALLINT,
    ADD COLUMN bio VARCHAR(600),
    ADD COLUMN rejection_reason VARCHAR(300),
    ADD COLUMN reviewed_at TIMESTAMPTZ,
    ADD CONSTRAINT chk_doctor_verification
        CHECK (verification IN ('PENDING', 'APPROVED', 'REJECTED'));

-- Existing (admin-created) doctors stay APPROVED; the column default above
-- only applies to rows that already exist. New self-applications set PENDING
-- explicitly in code.
CREATE INDEX idx_doctors_verification ON doctors (verification);
