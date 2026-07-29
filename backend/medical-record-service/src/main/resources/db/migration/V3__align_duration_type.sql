-- The Prescription entity maps durationDays as `int` (validated as SQL INTEGER),
-- but V1 created duration_days as SMALLINT, so `ddl-auto: validate` refused to
-- start the service. Widen to INTEGER to match the entity; the V1 range CHECK
-- (1..365) stays in force.
ALTER TABLE prescriptions ALTER COLUMN duration_days TYPE integer;
