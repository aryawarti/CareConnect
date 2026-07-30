package com.careconnect.queue.api;

import com.careconnect.queue.api.dto.ApiEnvelope;
import com.careconnect.queue.api.dto.QueueDtos.BoardSnapshot;
import com.careconnect.queue.api.dto.QueueDtos.CheckInRequest;
import com.careconnect.queue.api.dto.QueueDtos.MyQueueStatus;
import com.careconnect.queue.api.dto.QueueDtos.QueueEntryResponse;
import com.careconnect.queue.api.dto.QueueDtos.QueueSnapshot;
import com.careconnect.queue.api.dto.QueueDtos.WalkInRequest;
import com.careconnect.queue.application.QueueBroadcaster;
import com.careconnect.queue.application.QueueService;
import com.careconnect.queue.infrastructure.client.PatientClient;
import com.careconnect.queue.infrastructure.client.ProviderClient;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/queue")
public class QueueController {

    private static final Logger log = LoggerFactory.getLogger(QueueController.class);

    private final QueueService service;
    private final QueueBroadcaster broadcaster;
    private final PatientClient patientClient;
    private final ProviderClient providerClient;

    public QueueController(QueueService service, QueueBroadcaster broadcaster,
                           PatientClient patientClient, ProviderClient providerClient) {
        this.service = service;
        this.broadcaster = broadcaster;
        this.patientClient = patientClient;
        this.providerClient = providerClient;
    }

    /**
     * A DOCTOR may only read their own queue; STAFF and ADMIN run the whole
     * clinic and may read any. The doctor id in the URL is caller-supplied, so
     * it is never accepted as proof of identity — it is compared against the
     * profile actually linked to the caller's account.
     */
    private static boolean isStaff(Authentication auth) {
        return auth.getAuthorities().stream().anyMatch(a ->
                a.getAuthority().equals("ROLE_STAFF") || a.getAuthority().equals("ROLE_ADMIN"));
    }

    private void requireQueueAccess(UUID doctorId, Authentication auth) {
        if (isStaff(auth)) {
            return;
        }
        if (!providerClient.me().data().id().equals(doctorId)) {
            throw new AccessDeniedException("This queue belongs to another doctor");
        }
    }

    // ---- live streams -------------------------------------------------------

    /**
     * Server-Sent Events for the waiting-room board. Public by design — a lobby
     * kiosk has no credentials — so the payload is the redacted
     * {@link com.careconnect.queue.api.dto.QueueDtos.BoardSnapshot}: token
     * numbers, given names, waits. No surnames, no complaints.
     *
     * Privileged screens (the doctor console) also subscribe here, but treat an
     * event purely as a "something changed" signal and then re-fetch
     * {@code /console/{doctorId}} over an authenticated request. That keeps PHI
     * off the unauthenticated channel entirely.
     */
    @GetMapping(value = "/stream/{doctorId}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@PathVariable UUID doctorId) {
        SseEmitter emitter = broadcaster.subscribe(doctorId);
        try {
            emitter.send(SseEmitter.event().name("queue").data(service.boardSnapshot(doctorId)));
        } catch (Exception e) {
            // The client can vanish between subscribe and first send; that is
            // normal, but swallowing it silently once hid a broken stream for
            // days, so it is logged at debug rather than discarded.
            log.debug("SSE client disconnected before the first snapshot for doctor {}", doctorId, e);
        }
        return emitter;
    }

    /** Public lobby board — redacted, same projection as the stream. */
    @GetMapping("/board/{doctorId}")
    public ApiEnvelope<BoardSnapshot> board(@PathVariable UUID doctorId) {
        return ApiEnvelope.of(service.boardSnapshot(doctorId));
    }

    /** The doctor console's full picture: names and complaints, authenticated. */
    @GetMapping("/console/{doctorId}")
    @PreAuthorize("hasAnyRole('DOCTOR','STAFF','ADMIN')")
    public ApiEnvelope<QueueSnapshot> console(@PathVariable UUID doctorId, Authentication auth) {
        requireQueueAccess(doctorId, auth);
        return ApiEnvelope.of(service.snapshot(doctorId));
    }

    // ---- joining ------------------------------------------------------------

    /**
     * Joining today's queue.
     *
     * A PATIENT always checks *themselves* in: the patientId in the body is
     * ignored and replaced by the id resolved from their own account, the same
     * rule booking follows. Honouring the submitted value would let any patient
     * place any other patient into a queue. Staff check in whoever is at the desk.
     *
     * Names are resolved from patient-service and provider-service. They used to
     * be the literal strings "Patient" and "Doctor" — passed through a ternary
     * whose branches were identical — so every appointment check-in produced a
     * queue entry displaying "Patient" on the board and the doctor's console.
     */
    @PostMapping("/check-in")
    @PreAuthorize("hasAnyRole('STAFF','ADMIN','PATIENT')")
    public ApiEnvelope<QueueEntryResponse> checkIn(@Valid @RequestBody CheckInRequest request,
                                                   Authentication auth) {
        UUID patientId = isStaff(auth)
                ? request.patientId()
                : patientClient.me().data().id();
        if (patientId == null) {
            throw new IllegalArgumentException(
                    "patientId is required when staff check a patient in");
        }
        String patientName = patientClient.summary(patientId).data().fullName();
        String doctorName = providerClient.summary(request.doctorId()).data().fullName();
        return ApiEnvelope.of(QueueEntryResponse.from(
                service.checkIn(request, patientId, patientName, doctorName), null, null));
    }

    @PostMapping("/walk-in")
    @PreAuthorize("hasAnyRole('STAFF','ADMIN')")
    public ApiEnvelope<QueueEntryResponse> walkIn(@Valid @RequestBody WalkInRequest request) {
        return ApiEnvelope.of(QueueEntryResponse.from(service.walkIn(request), null, null));
    }

    // ---- doctor console -----------------------------------------------------

    @PostMapping("/doctor/{doctorId}/call-next")
    @PreAuthorize("hasAnyRole('DOCTOR','STAFF','ADMIN')")
    public ApiEnvelope<QueueEntryResponse> callNext(@PathVariable UUID doctorId,
                                                    Authentication auth) {
        requireQueueAccess(doctorId, auth);
        return ApiEnvelope.of(QueueEntryResponse.from(service.callNext(doctorId), null, null));
    }

    @PostMapping("/{id}/recall")
    @PreAuthorize("hasAnyRole('DOCTOR','STAFF','ADMIN')")
    public ApiEnvelope<QueueEntryResponse> recall(@PathVariable UUID id) {
        return ApiEnvelope.of(QueueEntryResponse.from(service.recall(id), null, null));
    }

    @PostMapping("/{id}/start")
    @PreAuthorize("hasAnyRole('DOCTOR','STAFF','ADMIN')")
    public ApiEnvelope<QueueEntryResponse> start(@PathVariable UUID id) {
        return ApiEnvelope.of(QueueEntryResponse.from(service.startConsultation(id), null, null));
    }

    @PostMapping("/{id}/complete")
    @PreAuthorize("hasAnyRole('DOCTOR','STAFF','ADMIN')")
    public ApiEnvelope<QueueEntryResponse> complete(@PathVariable UUID id) {
        return ApiEnvelope.of(QueueEntryResponse.from(service.completeConsultation(id), null, null));
    }

    @PostMapping("/{id}/left")
    @PreAuthorize("hasAnyRole('DOCTOR','STAFF','ADMIN')")
    public ApiEnvelope<QueueEntryResponse> markLeft(@PathVariable UUID id) {
        return ApiEnvelope.of(QueueEntryResponse.from(service.markLeft(id), null, null));
    }

    @PostMapping("/{id}/requeue")
    @PreAuthorize("hasAnyRole('DOCTOR','STAFF','ADMIN')")
    public ApiEnvelope<QueueEntryResponse> requeue(@PathVariable UUID id) {
        return ApiEnvelope.of(QueueEntryResponse.from(service.requeue(id), null, null));
    }

    // ---- patient view -------------------------------------------------------

    @GetMapping("/me")
    @PreAuthorize("hasRole('PATIENT')")
    public ApiEnvelope<MyQueueStatus> myStatus() {
        // Queue entries are keyed by patient-service's patient id, not the
        // identity user id — resolve through patient-service (ADR-004).
        QueueEntryResponse entry = service.myStatus(patientClient.me().data().id());
        if (entry == null) {
            return ApiEnvelope.of(new MyQueueStatus(false, null,
                    "You are not checked in today."));
        }
        String message = switch (entry.status()) {
            case "WAITING" -> entry.position() != null && entry.position() == 0
                    ? "You are next — please stay nearby."
                    : "You are number %d in the queue.".formatted((entry.position() == null ? 0 : entry.position()) + 1);
            case "CALLED" -> "You have been called — please go to the consultation room.";
            case "IN_CONSULTATION" -> "You are with the doctor.";
            case "COMPLETED" -> "Your consultation is complete.";
            case "SKIPPED" -> "You were called but not present. Please see reception.";
            default -> "";
        };
        return ApiEnvelope.of(new MyQueueStatus(true, entry, message));
    }

    // ---- reception / reporting ---------------------------------------------

    @GetMapping("/live")
    @PreAuthorize("hasAnyRole('STAFF','ADMIN')")
    public ApiEnvelope<List<QueueEntryResponse>> clinicLive() {
        return ApiEnvelope.of(service.clinicLive().stream()
                .map(e -> QueueEntryResponse.from(e, null, null)).toList());
    }

    @GetMapping("/doctor/{doctorId}/day")
    @PreAuthorize("hasAnyRole('DOCTOR','STAFF','ADMIN')")
    public ApiEnvelope<List<QueueEntryResponse>> doctorDay(
            @PathVariable UUID doctorId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            Authentication auth) {
        requireQueueAccess(doctorId, auth);
        return ApiEnvelope.of(service.doctorDay(doctorId, date == null ? service.today() : date)
                .stream().map(e -> QueueEntryResponse.from(e, null, null)).toList());
    }
}
