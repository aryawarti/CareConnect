package com.careconnect.queue.api;

import com.careconnect.queue.api.dto.ApiEnvelope;
import com.careconnect.queue.api.dto.QueueDtos.CheckInRequest;
import com.careconnect.queue.api.dto.QueueDtos.MyQueueStatus;
import com.careconnect.queue.api.dto.QueueDtos.QueueEntryResponse;
import com.careconnect.queue.api.dto.QueueDtos.QueueSnapshot;
import com.careconnect.queue.api.dto.QueueDtos.WalkInRequest;
import com.careconnect.queue.application.QueueBroadcaster;
import com.careconnect.queue.application.QueueService;
import com.careconnect.queue.infrastructure.client.PatientClient;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
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

    private final QueueService service;
    private final QueueBroadcaster broadcaster;
    private final PatientClient patientClient;

    public QueueController(QueueService service, QueueBroadcaster broadcaster,
                           PatientClient patientClient) {
        this.service = service;
        this.broadcaster = broadcaster;
        this.patientClient = patientClient;
    }

    // ---- live streams -------------------------------------------------------

    /**
     * Server-Sent Events: the waiting-room board and doctor console subscribe
     * here and receive a full snapshot on every change. Public by design — a
     * lobby display should not need credentials, and the payload carries only
     * token numbers and first names.
     */
    @GetMapping(value = "/stream/{doctorId}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@PathVariable UUID doctorId) {
        SseEmitter emitter = broadcaster.subscribe(doctorId);
        try {
            emitter.send(SseEmitter.event().name("queue").data(service.snapshot(doctorId)));
        } catch (Exception ignored) {
            // client vanished between subscribe and first send
        }
        return emitter;
    }

    @GetMapping("/board/{doctorId}")
    public ApiEnvelope<QueueSnapshot> board(@PathVariable UUID doctorId) {
        return ApiEnvelope.of(service.snapshot(doctorId));
    }

    // ---- joining ------------------------------------------------------------

    @PostMapping("/check-in")
    @PreAuthorize("hasAnyRole('STAFF','ADMIN','PATIENT')")
    public ApiEnvelope<QueueEntryResponse> checkIn(@Valid @RequestBody CheckInRequest request) {
        return ApiEnvelope.of(QueueEntryResponse.from(
                service.checkIn(request, request.patientId() == null ? "Patient" : "Patient",
                        "Doctor"), null, null));
    }

    @PostMapping("/walk-in")
    @PreAuthorize("hasAnyRole('STAFF','ADMIN')")
    public ApiEnvelope<QueueEntryResponse> walkIn(@Valid @RequestBody WalkInRequest request) {
        return ApiEnvelope.of(QueueEntryResponse.from(service.walkIn(request), null, null));
    }

    // ---- doctor console -----------------------------------------------------

    @PostMapping("/doctor/{doctorId}/call-next")
    @PreAuthorize("hasAnyRole('DOCTOR','STAFF','ADMIN')")
    public ApiEnvelope<QueueEntryResponse> callNext(@PathVariable UUID doctorId) {
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
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ApiEnvelope.of(service.doctorDay(doctorId, date == null ? service.today() : date)
                .stream().map(e -> QueueEntryResponse.from(e, null, null)).toList());
    }
}
