package com.careconnect.laboratory.domain;

import java.math.BigDecimal;

/**
 * How a measured value compares to reference and critical ranges. CRITICAL is
 * not merely "very abnormal" — it is a value that, unaddressed, threatens the
 * patient, and it triggers an immediate alert to the ordering doctor.
 */
public enum ResultFlag {
    NORMAL, HIGH, LOW, CRITICAL;

    public boolean isCritical() {
        return this == CRITICAL;
    }

    /** Classifies a numeric value; non-numeric results are treated as NORMAL text. */
    public static ResultFlag classify(BigDecimal value, BigDecimal refLow, BigDecimal refHigh,
                                      BigDecimal criticalLow, BigDecimal criticalHigh) {
        if (value == null) {
            return NORMAL;
        }
        if (criticalLow != null && value.compareTo(criticalLow) < 0) return CRITICAL;
        if (criticalHigh != null && value.compareTo(criticalHigh) > 0) return CRITICAL;
        if (refLow != null && value.compareTo(refLow) < 0) return LOW;
        if (refHigh != null && value.compareTo(refHigh) > 0) return HIGH;
        return NORMAL;
    }
}
