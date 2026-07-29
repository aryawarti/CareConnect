package com.careconnect.appointment.domain;

import java.util.Map;
import java.util.Set;

public enum AppointmentStatus {
    REQUESTED, CONFIRMED, COMPLETED, CANCELLED, NO_SHOW;

    private static final Map<AppointmentStatus, Set<AppointmentStatus>> TRANSITIONS = Map.of(
            REQUESTED, Set.of(CONFIRMED, CANCELLED),
            CONFIRMED, Set.of(COMPLETED, CANCELLED, NO_SHOW),
            COMPLETED, Set.of(),
            CANCELLED, Set.of(),
            NO_SHOW, Set.of());

    public boolean canTransitionTo(AppointmentStatus target) {
        return TRANSITIONS.get(this).contains(target);
    }

    /** Statuses that hold the time slot (mirrors the DB exclusion constraint's WHERE). */
    public boolean blocksSlot() {
        return this == REQUESTED || this == CONFIRMED;
    }
}
