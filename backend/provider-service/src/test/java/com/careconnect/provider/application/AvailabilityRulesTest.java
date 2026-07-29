package com.careconnect.provider.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.careconnect.provider.domain.AvailabilitySlot;
import java.time.LocalTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AvailabilityRulesTest {

    private final UUID doc = UUID.randomUUID();

    private AvailabilitySlot slot(int day, int fromHour, int toHour) {
        return new AvailabilitySlot(doc, day, LocalTime.of(fromHour, 0), LocalTime.of(toHour, 0), 30);
    }

    @Test
    void overlappingWindowsOnSameDayAreDetected() {
        assertThat(slot(1, 9, 13).overlaps(slot(1, 12, 16))).isTrue();
        assertThat(slot(1, 9, 13).overlaps(slot(1, 13, 16))).isFalse();  // touching ≠ overlapping
    }

    @Test
    void sameWindowOnDifferentDaysDoesNotOverlap() {
        assertThat(slot(1, 9, 13).overlaps(slot(2, 9, 13))).isFalse();
    }

    @Test
    void containedWindowOverlaps() {
        assertThat(slot(5, 9, 17).overlaps(slot(5, 11, 12))).isTrue();
    }
}
