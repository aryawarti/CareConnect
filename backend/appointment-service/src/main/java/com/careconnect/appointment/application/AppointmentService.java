package com.careconnect.appointment.application;

import com.careconnect.appointment.api.dto.AppointmentDtos.BookRequest;
import com.careconnect.appointment.api.dto.AppointmentDtos.FreeSlot;
import com.careconnect.appointment.domain.Appointment;
import com.careconnect.appointment.domain.AppointmentConflictException;
import com.careconnect.appointment.domain.AppointmentNotFoundException;
import com.careconnect.appointment.domain.AppointmentStatus;
import com.careconnect.appointment.domain.DependencyUnavailableException;
import com.careconnect.appointment.domain.StatusHistoryEntry;
import com.careconnect.appointment.infrastructure.client.PatientClient;
import com.careconnect.appointment.infrastructure.client.ProviderClient;
import com.careconnect.appointment.infrastructure.repository.AppointmentRepository;
import com.careconnect.appointment.infrastructure.messaging.DomainEventPublisher;
import com.careconnect.appointment.infrastructure.messaging.KafkaTopicsConfig;
import com.careconnect.appointment.infrastructure.repository.StatusHistoryRepository;
import java.util.Map;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AppointmentService {

    private static final Logger log = LoggerFactory.getLogger(AppointmentService.class);

    private final AppointmentRepository appointments;
    private final StatusHistoryRepository history;
    private final PatientClient patientClient;
    private final ProviderClient providerClient;
    private final DomainEventPublisher events;
    private final ZoneId clinicZone;
    private final Counter booked;
    private final Counter conflicts;

    public AppointmentService(AppointmentRepository appointments, StatusHistoryRepository history,
                              PatientClient patientClient, ProviderClient providerClient,
                              DomainEventPublisher events, MeterRegistry meters,
                              @Value("${careconnect.clinic.zone:Asia/Kolkata}") String clinicZone) {
        this.appointments = appointments;
        this.history = history;
        this.patientClient = patientClient;
        this.providerClient = providerClient;
        this.events = events;
        this.clinicZone = ZoneId.of(clinicZone);
        // Domain metrics: the numbers a clinic manager would actually ask about.
        this.booked = Counter.builder("careconnect.appointments.booked")
                .description("Appointments successfully booked").register(meters);
        this.conflicts = Counter.builder("careconnect.appointments.conflicts")
                .description("Bookings rejected because the slot was taken").register(meters);
    }

    /** Free slots for a doctor on a date: availability windows minus held appointments. */
    @Transactional(readOnly = true)
    public List<FreeSlot> freeSlots(UUID doctorId, LocalDate date) {
        ProviderClient.BookingInfo info = fetchBookingInfo(doctorId, date);
        if (!info.active() || info.dayOff() || info.windows().isEmpty()) {
            return List.of();
        }
        Instant dayStart = date.atStartOfDay(clinicZone).toInstant();
        Instant dayEnd = date.plusDays(1).atStartOfDay(clinicZone).toInstant();
        List<Appointment> held = appointments.blockingAppointments(doctorId, dayStart, dayEnd);

        List<FreeSlot> free = new ArrayList<>();
        for (ProviderClient.Window window : info.windows()) {
            LocalTime t = window.start();
            while (!t.plusMinutes(window.slotMinutes()).isAfter(window.end())) {
                Instant slotStart = date.atTime(t).atZone(clinicZone).toInstant();
                Instant slotEnd = slotStart.plusSeconds(window.slotMinutes() * 60L);
                boolean taken = held.stream().anyMatch(a ->
                        a.getStartAt().isBefore(slotEnd) && slotStart.isBefore(a.getEndAt()));
                if (!taken && slotStart.isAfter(Instant.now())) {
                    free.add(new FreeSlot(slotStart, slotEnd));
                }
                t = t.plusMinutes(window.slotMinutes());
            }
        }
        return free;
    }

    /**
     * Booking. PATIENT callers always book for themselves (patientId from
     * their linked patient record); staff pass an explicit patientId.
     * Concurrency: the app-level check narrows the race; the DB exclusion
     * constraint eliminates it — a lost race surfaces as 409, not double-booking.
     */
    @Transactional
    public Appointment book(BookRequest request, UUID targetPatientId) {
        PatientClient.PatientSummary patient = fetchPatient(targetPatientId);
        if (!patient.active()) {
            throw new IllegalArgumentException("Patient record is inactive");
        }
        LocalDate date = LocalDate.ofInstant(request.startAt(), clinicZone);
        ProviderClient.BookingInfo info = fetchBookingInfo(request.doctorId(), date);
        if (!info.active()) {
            throw new IllegalArgumentException("Doctor is not accepting appointments");
        }
        ProviderClient.Window window = containingWindow(info, request.startAt(), date);
        Instant endAt = request.startAt().plusSeconds(window.slotMinutes() * 60L);

        Appointment appointment = new Appointment(patient.id(), request.doctorId(),
                request.startAt(), endAt, request.reason(), info.consultationFee(),
                patient.fullName(), info.fullName());
        try {
            appointments.saveAndFlush(appointment);
        } catch (DataIntegrityViolationException e) {
            // Exclusion constraint: someone else took the slot between check and insert.
            conflicts.increment();
            throw new AppointmentConflictException("This slot has just been taken — pick another");
        }
        history.save(new StatusHistoryEntry(appointment.getId(), null, AppointmentStatus.REQUESTED));
        log.info("appointment booked id={} doctor={} start={}",
                appointment.getId(), request.doctorId(), request.startAt());
        publishEvent("AppointmentRequested", appointment);
        booked.increment();
        return appointment;
    }

    @Transactional(readOnly = true)
    public Appointment get(UUID id) {
        return appointments.findById(id).orElseThrow(() -> new AppointmentNotFoundException(id));
    }

    @Transactional(readOnly = true)
    public Page<Appointment> forPatient(UUID patientId, Pageable pageable) {
        return appointments.findByPatientIdOrderByStartAtDesc(patientId, pageable);
    }

    /** Every appointment in the clinic on a date (staff dashboard). */
    @Transactional(readOnly = true)
    public List<Appointment> clinicDay(LocalDate date) {
        return appointments.findByStartAtBetweenOrderByStartAt(
                date.atStartOfDay(clinicZone).toInstant(),
                date.plusDays(1).atStartOfDay(clinicZone).toInstant());
    }

    @Transactional(readOnly = true)
    public List<Appointment> doctorDay(UUID doctorId, LocalDate date) {
        return appointments.doctorDay(doctorId,
                date.atStartOfDay(clinicZone).toInstant(),
                date.plusDays(1).atStartOfDay(clinicZone).toInstant());
    }

    @Transactional
    public Appointment confirm(UUID id)  { return transition(id, Appointment::confirm); }

    /**
     * Completion driven by the queue: a REQUESTED appointment is auto-confirmed
     * first, because a patient who was actually seen was evidently expected —
     * refusing to complete on a paperwork technicality would be worse than
     * silently confirming.
     */
    @Transactional
    public Appointment completeFromConsultation(UUID id) {
        Appointment appointment = get(id);
        if (appointment.getStatus() == AppointmentStatus.REQUESTED) {
            transition(id, Appointment::confirm);
        }
        return transition(id, Appointment::complete);
    }

    @Transactional
    public Appointment complete(UUID id) { return transition(id, Appointment::complete); }

    @Transactional
    public Appointment noShow(UUID id)   { return transition(id, Appointment::markNoShow); }

    @Transactional
    public Appointment cancelAsStaff(UUID id) { return transition(id, Appointment::cancel); }

    /** Resolves which doctor profile the signed-in user is. */
    public UUID resolveOwnDoctorId(UUID userId) {
        try {
            return providerClient.me().data().id();
        } catch (feign.FeignException.NotFound e) {
            throw new IllegalArgumentException("No doctor profile is linked to your account");
        } catch (feign.FeignException e) {
            throw new DependencyUnavailableException("Provider service unavailable", e);
        }
    }

    /** Requests waiting for this doctor to accept or decline. */
    @Transactional(readOnly = true)
    public List<Appointment> pendingRequestsForDoctor(UUID doctorId) {
        return appointments.findByDoctorIdAndStatusOrderByStartAtAsc(
                doctorId, AppointmentStatus.REQUESTED);
    }

    /**
     * The doctor's own decision on a request. A doctor may only act on their
     * own appointments — checked against the profile linked to their account,
     * never against an id supplied by the caller.
     */
    @Transactional
    public Appointment confirmAsDoctor(UUID id, UUID doctorId) {
        requireOwnAppointment(id, doctorId);
        return transition(id, Appointment::confirm);
    }

    @Transactional
    public Appointment declineAsDoctor(UUID id, UUID doctorId) {
        requireOwnAppointment(id, doctorId);
        return transition(id, Appointment::cancel);
    }

    private void requireOwnAppointment(UUID id, UUID doctorId) {
        Appointment appointment = get(id);
        if (!appointment.getDoctorId().equals(doctorId)) {
            throw new AccessDeniedException("This appointment belongs to another doctor");
        }
    }

    @Transactional
    public Appointment cancelAsPatient(UUID id, UUID callerPatientId) {
        Appointment appointment = get(id);
        if (!appointment.isOwnedByPatientUser(callerPatientId)) {
            throw new AccessDeniedException("Not your appointment");
        }
        return transition(id, a -> a.cancelAsPatient(Instant.now()));
    }

    /** Resolves the caller's own patientId (PATIENT role) via patient-service. */
    public UUID resolveOwnPatientId(UUID userId) {
        try {
            return patientClient.me().data().id();
        } catch (feign.FeignException.NotFound e) {
            throw new IllegalArgumentException(
                    "No patient profile is linked to your account yet — contact the clinic");
        } catch (feign.FeignException e) {
            throw new DependencyUnavailableException("Patient service unavailable", e);
        }
    }

    private Appointment transition(UUID id, Consumer<Appointment> action) {
        Appointment appointment = get(id);
        AppointmentStatus from = appointment.getStatus();
        action.accept(appointment);
        history.save(new StatusHistoryEntry(id, from, appointment.getStatus()));
        log.info("appointment {} {} -> {}", id, from, appointment.getStatus());
        publishEvent(switch (appointment.getStatus()) {
            case CONFIRMED -> "AppointmentConfirmed";
            case COMPLETED -> "AppointmentCompleted";
            case CANCELLED -> "AppointmentCancelled";
            case NO_SHOW -> "AppointmentNoShow";
            case REQUESTED -> "AppointmentRequested";
        }, appointment);
        return appointment;
    }

    /** Self-contained payload: consumers never call back (communication.md). */
    private void publishEvent(String type, Appointment a) {
        events.publish(KafkaTopicsConfig.APPOINTMENT_EVENTS, type, a.getId(), Map.of(
                "appointmentId", a.getId().toString(),
                "patientId", a.getPatientId().toString(),
                "doctorId", a.getDoctorId().toString(),
                "patientName", a.getPatientName(),
                "doctorName", a.getDoctorName(),
                "startAt", a.getStartAt().toString(),
                "endAt", a.getEndAt().toString(),
                "fee", a.getFeeSnapshot().toString(),
                "status", a.getStatus().name()));
    }

    private ProviderClient.Window containingWindow(ProviderClient.BookingInfo info,
                                                   Instant startAt, LocalDate date) {
        if (info.dayOff()) {
            throw new IllegalArgumentException("The doctor is not available on this date");
        }
        LocalTime time = LocalTime.ofInstant(startAt, clinicZone);
        return info.windows().stream()
                .filter(w -> !time.isBefore(w.start())
                        && !time.plusMinutes(w.slotMinutes()).isAfter(w.end())
                        && time.getMinute() % w.slotMinutes() == w.start().getMinute() % w.slotMinutes())
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Start time is outside the doctor's availability"));
    }

    private PatientClient.PatientSummary fetchPatient(UUID patientId) {
        try {
            return patientClient.summary(patientId).data();
        } catch (feign.FeignException.NotFound e) {
            throw new IllegalArgumentException("Unknown patient");
        } catch (feign.FeignException e) {
            throw new DependencyUnavailableException("Patient service unavailable", e);
        }
    }

    private ProviderClient.BookingInfo fetchBookingInfo(UUID doctorId, LocalDate date) {
        try {
            return providerClient.bookingInfo(doctorId, date).data();
        } catch (feign.FeignException.NotFound e) {
            throw new IllegalArgumentException("Unknown doctor");
        } catch (feign.FeignException e) {
            throw new DependencyUnavailableException("Provider service unavailable", e);
        }
    }
}
