package com.careconnect.laboratory.domain;

import java.util.Map;
import java.util.Set;

/**
 * The order's journey through the lab. Transitions are validated centrally so
 * "enter results before collecting the sample" is impossible by construction.
 */
public enum OrderStatus {
    ORDERED, COLLECTED, IN_PROCESS, REPORTED, VERIFIED, REJECTED, CANCELLED;

    private static final Map<OrderStatus, Set<OrderStatus>> NEXT = Map.of(
            ORDERED,    Set.of(COLLECTED, CANCELLED),
            COLLECTED,  Set.of(IN_PROCESS, REJECTED),
            IN_PROCESS, Set.of(REPORTED, REJECTED),
            REPORTED,   Set.of(VERIFIED, IN_PROCESS),   // verifier can send back
            VERIFIED,   Set.of(),
            REJECTED,   Set.of(),
            CANCELLED,  Set.of());

    public boolean canMoveTo(OrderStatus target) {
        return NEXT.get(this).contains(target);
    }
}
