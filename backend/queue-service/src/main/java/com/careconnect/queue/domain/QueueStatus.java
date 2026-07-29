package com.careconnect.queue.domain;

public enum QueueStatus {
    /** Checked in, waiting to be called. */
    WAITING,
    /** Called over the display/announcement; not yet in the room. */
    CALLED,
    /** With the doctor now. */
    IN_CONSULTATION,
    /** Consultation finished — triggers the downstream event chain. */
    COMPLETED,
    /** Called repeatedly, never appeared — requeued at the back or dropped. */
    SKIPPED,
    /** Gave up and left before being seen (tracked: it is a quality metric). */
    LEFT;

    public boolean isActive() {
        return this == WAITING || this == CALLED || this == IN_CONSULTATION;
    }
}
