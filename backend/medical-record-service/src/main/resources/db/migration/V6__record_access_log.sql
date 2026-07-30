-- Who read whose chart.
--
-- Access CONTROL decides who may read a record; this decides nothing. It is the
-- evidence of what actually happened, and it is the control a clinical system is
-- judged on: "only the treating doctor can open this chart" is a claim, and
-- without a log there is no way to check whether it held.
--
-- Append-only by construction: no application code updates or deletes a row, and
-- nothing here has an updated_at. A log that can be edited by the people it
-- observes is not evidence.
CREATE TABLE record_access_log (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    -- Who looked. actor_user_id is the identity subject (the JWT sub), NOT the
    -- doctor/patient id — those id spaces differ (ADR-004), and the account is
    -- what you can hold responsible.
    actor_user_id   UUID NOT NULL,
    actor_role      VARCHAR(20) NOT NULL,
    actor_name      VARCHAR(160),

    -- Whose data. patient_id is always set, even when the access was to a single
    -- encounter, so "everything ever read about this patient" is one indexed query.
    patient_id      UUID NOT NULL,
    encounter_id    UUID,

    action          VARCHAR(40) NOT NULL,
    -- True when someone reads their own record. Kept rather than filtered out:
    -- a patient's own access is unremarkable but still part of the trail, and
    -- excluding it would mean the log cannot answer "was this account used?".
    self_access     BOOLEAN NOT NULL DEFAULT FALSE,

    accessed_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    -- Ties an entry back to the request across every service it touched.
    correlation_id  VARCHAR(64),

    CONSTRAINT chk_access_action CHECK
        (action IN ('VIEW_ENCOUNTER', 'LIST_PATIENT_HISTORY', 'LIST_OWN_HISTORY'))
);

-- "Who has seen this patient's chart" — the patient-facing and audit query.
CREATE INDEX idx_access_patient ON record_access_log (patient_id, accessed_at DESC);
-- "What has this account been reading" — the question asked when an account is
-- suspected of browsing records it has no business in.
CREATE INDEX idx_access_actor ON record_access_log (actor_user_id, accessed_at DESC);
