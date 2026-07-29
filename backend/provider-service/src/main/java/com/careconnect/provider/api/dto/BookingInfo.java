package com.careconnect.provider.api.dto;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

/**
 * Everything appointment-service needs to validate a booking for one doctor
 * on one date, in a single call: active flag, fee (snapshot source), and the
 * day's availability windows (empty when it's an exception day).
 */
public record BookingInfo(
        UUID id,
        boolean active,
        String fullName,
        BigDecimal consultationFee,
        boolean dayOff,
        List<Window> windows) {

    public record Window(LocalTime start, LocalTime end, int slotMinutes) { }
}
