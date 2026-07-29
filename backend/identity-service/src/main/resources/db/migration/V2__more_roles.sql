-- Additional staff roles introduced with the clinical modules (lab, radiology,
-- pharmacy, nursing, billing). Idempotent so re-running is safe.
INSERT INTO roles (name) VALUES
    ('LAB_TECHNICIAN'), ('RADIOLOGIST'), ('PHARMACIST'),
    ('NURSE'), ('BILLING'), ('SUPER_ADMIN')
ON CONFLICT (name) DO NOTHING;
