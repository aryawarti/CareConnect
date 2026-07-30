package com.careconnect.medicalrecord.domain;

/** What was read. Mirrored by the CHECK constraint in V6__record_access_log.sql. */
public enum RecordAccessAction {

    /** One encounter opened in full — notes, diagnoses, prescriptions. */
    VIEW_ENCOUNTER,

    /** A clinician or staff member listed a patient's visit history. */
    LIST_PATIENT_HISTORY,

    /** A patient listed their own history. Logged too — see the entity comment. */
    LIST_OWN_HISTORY
}
