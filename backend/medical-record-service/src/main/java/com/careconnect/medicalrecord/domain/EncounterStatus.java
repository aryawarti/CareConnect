package com.careconnect.medicalrecord.domain;

public enum EncounterStatus {
    /** Doctor can still edit notes freely. */
    OPEN,
    /** Signed off — immutable; further changes must be amendments. */
    SIGNED,
    /** Signed, then amended at least once (amendments keep the prior text). */
    AMENDED
}
