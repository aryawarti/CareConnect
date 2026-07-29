package com.careconnect.queue.domain;

/**
 * Triage level. Ordering weight is deliberate: an EMERGENCY entering at 11:00
 * is called before a NORMAL who arrived at 09:00, but two EMERGENCIES are
 * still served in arrival order — priority reorders groups, never individuals
 * within a group.
 */
public enum QueuePriority {
    EMERGENCY(0), URGENT(1), NORMAL(2);

    private final int weight;

    QueuePriority(int weight) {
        this.weight = weight;
    }

    public int weight() {
        return weight;
    }
}
