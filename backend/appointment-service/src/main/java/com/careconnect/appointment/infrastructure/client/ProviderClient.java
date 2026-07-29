package com.careconnect.appointment.infrastructure.client;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "provider-service", url = "${careconnect.clients.provider-url:}")
public interface ProviderClient {

    /** The calling doctor's own profile (identity headers are forwarded). */
    @GetMapping("/api/providers/me")
    Envelope<DoctorProfile> me();

    record DoctorProfile(UUID id, String firstName, String lastName, String specialty) { }

    @GetMapping("/api/providers/doctors/{id}/booking-info")
    Envelope<BookingInfo> bookingInfo(@PathVariable("id") UUID id,
                                      @RequestParam("date")
                                      @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date);

    record BookingInfo(UUID id, boolean active, String fullName,
                       BigDecimal consultationFee, boolean dayOff, List<Window> windows) { }

    record Window(LocalTime start, LocalTime end, int slotMinutes) { }

    record Envelope<T>(T data) { }
}
