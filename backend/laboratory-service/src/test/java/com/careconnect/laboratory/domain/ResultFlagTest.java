package com.careconnect.laboratory.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class ResultFlagTest {

    private ResultFlag classify(String value, String low, String high, String cLow, String cHigh) {
        return ResultFlag.classify(new BigDecimal(value),
                low == null ? null : new BigDecimal(low),
                high == null ? null : new BigDecimal(high),
                cLow == null ? null : new BigDecimal(cLow),
                cHigh == null ? null : new BigDecimal(cHigh));
    }

    @Test
    void inRangeValueIsNormal() {
        assertThat(classify("14", "13", "17", "7", "20")).isEqualTo(ResultFlag.NORMAL);
    }

    @Test
    void aboveReferenceButNotCriticalIsHigh() {
        assertThat(classify("18", "13", "17", "7", "20")).isEqualTo(ResultFlag.HIGH);
    }

    @Test
    void belowReferenceButNotCriticalIsLow() {
        assertThat(classify("10", "13", "17", "7", "20")).isEqualTo(ResultFlag.LOW);
    }

    @Test
    void belowCriticalLowIsCritical() {
        // Haemoglobin 5 with critical-low 7 — a life-threatening value
        assertThat(classify("5", "13", "17", "7", "20")).isEqualTo(ResultFlag.CRITICAL);
    }

    @Test
    void aboveCriticalHighIsCritical() {
        assertThat(classify("25", "13", "17", "7", "20")).isEqualTo(ResultFlag.CRITICAL);
    }

    @Test
    void criticalTakesPrecedenceOverHighLow() {
        // Even though 25 is "high", it is beyond critical-high, so CRITICAL wins
        assertThat(classify("25", "13", "17", "7", "20").isCritical()).isTrue();
    }

    @Test
    void missingCriticalBoundsStillFlagHighLow() {
        assertThat(classify("210", "125", "200", null, null)).isEqualTo(ResultFlag.HIGH);
        assertThat(classify("150", "125", "200", null, null)).isEqualTo(ResultFlag.NORMAL);
    }

    @Test
    void nonNumericValueIsTreatedAsNormalText() {
        assertThat(ResultFlag.classify(null, new BigDecimal("1"), new BigDecimal("2"), null, null))
                .isEqualTo(ResultFlag.NORMAL);
    }
}
