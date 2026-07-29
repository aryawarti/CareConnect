-- The AvailabilitySlot entity maps dayOfWeek and slotMinutes as `int`, which
-- Hibernate validates against SQL INTEGER — but V1 created them as SMALLINT, so
-- `ddl-auto: validate` refused to start the service (schema-validation: found
-- int2, expecting integer). Widen the columns to INTEGER to match the entity.
-- The range CHECK constraints from V1 remain in force after the type change.
ALTER TABLE availability_slots ALTER COLUMN day_of_week  TYPE integer;
ALTER TABLE availability_slots ALTER COLUMN slot_minutes TYPE integer;
