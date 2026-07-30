-- Supports the authorization check on GET /api/records/patient/{patientId}:
-- "has this doctor ever treated this patient?" (existsByPatientIdAndDoctorId).
--
-- idx_encounters_patient (patient_id, occurred_at DESC) could serve the query,
-- but would scan every encounter for that patient to test doctor_id. This runs
-- on every doctor read of a patient history, so it gets an index that answers
-- the existence question directly.
CREATE INDEX idx_encounters_patient_doctor ON encounters (patient_id, doctor_id);
