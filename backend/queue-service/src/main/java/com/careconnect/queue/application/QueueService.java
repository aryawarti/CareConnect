package com.careconnect.queue.application;

import com.careconnect.queue.api.dto.QueueDtos.BoardSnapshot;
import com.careconnect.queue.api.dto.QueueDtos.CheckInRequest;
import com.careconnect.queue.api.dto.QueueDtos.QueueEntryResponse;
import com.careconnect.queue.api.dto.QueueDtos.QueueSnapshot;
import com.careconnect.queue.api.dto.QueueDtos.WalkInRequest;
import com.careconnect.queue.domain.QueueEntry;
import com.careconnect.queue.domain.QueueEntryNotFoundException;
import com.careconnect.queue.domain.QueuePriority;
import com.careconnect.queue.domain.QueueStatus;
import com.careconnect.queue.domain.TokenCounter;
import com.careconnect.queue.infrastructure.messaging.DomainEventPublisher;
import com.careconnect.queue.infrastructure.messaging.KafkaTopicsConfig;
import com.careconnect.queue.infrastructure.repository.QueueEntryRepository;
import com.careconnect.queue.infrastructure.repository.TokenCounterRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class QueueService {

    private static final Logger log = LoggerFactory.getLogger(QueueService.class);

    private final QueueEntryRepository entries;
    private final TokenCounterRepository counters;
    private final WaitTimeEstimator estimator;
    private final QueueBroadcaster broadcaster;
    private final DomainEventPublisher events;
    private final ZoneId clinicZone;
    private final Counter checkIns;
    private final Counter walkAways;

    public QueueService(QueueEntryRepository entries, TokenCounterRepository counters,
                        WaitTimeEstimator estimator, QueueBroadcaster broadcaster,
                        DomainEventPublisher events, MeterRegistry meters,
                        @Value("${careconnect.clinic.zone:Asia/Kolkata}") String clinicZone) {
        this.entries = entries;
        this.counters = counters;
        this.estimator = estimator;
        this.broadcaster = broadcaster;
        this.events = events;
        this.clinicZone = ZoneId.of(clinicZone);
        this.checkIns = Counter.builder("careconnect.queue.checkins")
                .description("Patients who joined a queue").register(meters);
        this.walkAways = Counter.builder("careconnect.queue.walkaways")
                .description("Patients who left before being seen").register(meters);
    }

    public LocalDate today() {
        return LocalDate.now(clinicZone);
    }

    // ---- joining the queue --------------------------------------------------

    /**
     * {@code patientId} is passed separately rather than read from the request:
     * for a PATIENT caller it is the id resolved from their own account, not the
     * one they submitted. Names are resolved by the caller too — this service
     * never invents them.
     */
    @Transactional
    public QueueEntry checkIn(CheckInRequest request, UUID patientId,
                              String patientName, String doctorName) {
        if (request.appointmentId() != null) {
            var existing = entries.findByAppointmentId(request.appointmentId());
            if (existing.isPresent()) {
                return existing.get();       // idempotent: double-tap on "check in"
            }
        }
        return join(request.appointmentId(), patientId, request.doctorId(),
                patientName, doctorName, request.complaint(), request.priority());
    }

    @Transactional
    public QueueEntry walkIn(WalkInRequest request) {
        return join(null, request.patientId(), request.doctorId(),
                request.patientName(), request.doctorName(),
                request.complaint(), request.priority());
    }

    private QueueEntry join(UUID appointmentId, UUID patientId, UUID doctorId,
                            String patientName, String doctorName,
                            String complaint, QueuePriority priority) {
        LocalDate date = today();
        String token = nextToken(doctorId, date);
        QueueEntry entry = entries.save(new QueueEntry(appointmentId, patientId, doctorId,
                patientName, doctorName, token, date, priority, complaint));
        checkIns.increment();
        log.info("queue check-in token={} doctor={} priority={}", token, doctorId, entry.getPriority());
        publish("PatientCheckedIn", entry);
        broadcastAfterCommit(doctorId);
        return entry;
    }

    /** Row-locked per doctor/day so concurrent check-ins can't duplicate a token. */
    private String nextToken(UUID doctorId, LocalDate date) {
        TokenCounter counter = counters.lockFor(doctorId, date)
                .orElseGet(() -> counters.save(new TokenCounter(doctorId, date)));
        return "T-%03d".formatted(counter.next());
    }

    // ---- calling and serving ------------------------------------------------

    /** Calls the next patient in fairness order; returns empty if nobody waits. */
    @Transactional
    public QueueEntry callNext(UUID doctorId) {
        List<QueueEntry> live = entries.liveQueue(doctorId, today());
        QueueEntry next = live.stream()
                .filter(e -> e.getStatus() == QueueStatus.WAITING)
                .findFirst()
                .orElseThrow(() -> new QueueEntryNotFoundException("Nobody is waiting"));
        next.call();
        log.info("calling token={} attempt={}", next.getTokenNumber(), next.getCallAttempts());
        publish("PatientCalled", next);
        broadcastAfterCommit(doctorId);
        return next;
    }

    @Transactional
    public QueueEntry recall(UUID entryId) {
        QueueEntry entry = get(entryId);
        entry.call();
        if (entry.exhaustedCallAttempts()) {
            entry.skip();
            log.info("token={} skipped after {} calls", entry.getTokenNumber(), entry.getCallAttempts());
            publish("PatientSkipped", entry);
        } else {
            publish("PatientCalled", entry);
        }
        broadcastAfterCommit(entry.getDoctorId());
        return entry;
    }

    @Transactional
    public QueueEntry startConsultation(UUID entryId) {
        QueueEntry entry = get(entryId);
        entry.startConsultation();
        publish("ConsultationStarted", entry);
        broadcastAfterCommit(entry.getDoctorId());
        return entry;
    }

    /**
     * The pivotal action in the whole system: ending a consultation publishes
     * ConsultationCompleted, which appointment-service consumes to complete the
     * appointment — which in turn opens the medical record, issues the invoice
     * and notifies the patient. One click by a doctor, five services reacting.
     */
    @Transactional
    public QueueEntry completeConsultation(UUID entryId) {
        QueueEntry entry = get(entryId);
        entry.completeConsultation();
        log.info("consultation complete token={} duration={}s",
                entry.getTokenNumber(), entry.getConsultationSeconds());
        publish("ConsultationCompleted", entry);
        broadcastAfterCommit(entry.getDoctorId());
        return entry;
    }

    @Transactional
    public QueueEntry markLeft(UUID entryId) {
        QueueEntry entry = get(entryId);
        entry.markLeft();
        walkAways.increment();
        publish("PatientLeft", entry);
        broadcastAfterCommit(entry.getDoctorId());
        return entry;
    }

    @Transactional
    public QueueEntry requeue(UUID entryId) {
        QueueEntry entry = get(entryId);
        entry.requeue();
        publish("PatientRequeued", entry);
        broadcastAfterCommit(entry.getDoctorId());
        return entry;
    }

    // ---- reads --------------------------------------------------------------

    @Transactional(readOnly = true)
    public QueueEntry get(UUID id) {
        return entries.findById(id).orElseThrow(() -> new QueueEntryNotFoundException(id));
    }

    /** Full live picture for a doctor's screen or the waiting-room board. */
    @Transactional(readOnly = true)
    public QueueSnapshot snapshot(UUID doctorId) {
        List<QueueEntry> live = entries.liveQueue(doctorId, today());
        int average = estimator.averageConsultationMinutes(doctorId);
        boolean someoneInside = live.stream().anyMatch(e -> e.getStatus() == QueueStatus.IN_CONSULTATION);

        List<QueueEntryResponse> responses = new ArrayList<>();
        QueueEntryResponse nowServing = null;
        int position = 0;
        for (QueueEntry entry : live) {
            if (entry.getStatus() == QueueStatus.IN_CONSULTATION) {
                QueueEntryResponse served = QueueEntryResponse.from(entry, null, null);
                nowServing = served;
                responses.add(served);
                continue;
            }
            int eta = estimator.estimateWaitMinutes(doctorId, position, someoneInside);
            responses.add(QueueEntryResponse.from(entry, position, eta));
            position++;
        }
        String doctorName = live.isEmpty() ? "" : live.get(0).getDoctorName();
        long waiting = live.stream().filter(e -> e.getStatus() == QueueStatus.WAITING).count();
        return new QueueSnapshot(doctorId, doctorName, (int) waiting, average,
                nowServing, responses, Instant.now());
    }

    /**
     * The same picture, redacted for the unauthenticated lobby board and its
     * SSE stream. Everything public goes through here — see
     * {@link com.careconnect.queue.api.dto.QueueDtos.BoardSnapshot}.
     */
    @Transactional(readOnly = true)
    public BoardSnapshot boardSnapshot(UUID doctorId) {
        return BoardSnapshot.from(snapshot(doctorId));
    }

    /** A patient's own entry today, with live position and ETA. */
    @Transactional(readOnly = true)
    public QueueEntryResponse myStatus(UUID patientId) {
        List<QueueEntry> mine = entries.findByPatientIdAndQueueDateOrderByCheckedInAtDesc(
                patientId, today());
        QueueEntry entry = mine.stream().filter(e -> e.getStatus().isActive()).findFirst()
                .orElse(mine.isEmpty() ? null : mine.get(0));
        if (entry == null) {
            return null;
        }
        QueueSnapshot snapshot = snapshot(entry.getDoctorId());
        return snapshot.entries().stream()
                .filter(e -> e.id().equals(entry.getId()))
                .findFirst()
                .orElse(QueueEntryResponse.from(entry, null, null));
    }

    @Transactional(readOnly = true)
    public List<QueueEntry> doctorDay(UUID doctorId, LocalDate date) {
        return entries.findByDoctorIdAndQueueDateOrderByCheckedInAtAsc(doctorId, date);
    }

    /** Clinic-wide live board (all doctors) for reception screens. */
    @Transactional(readOnly = true)
    public List<QueueEntry> clinicLive() {
        return entries.findByQueueDateAndStatusIn(today(),
                List.of(QueueStatus.WAITING, QueueStatus.CALLED, QueueStatus.IN_CONSULTATION));
    }

    // ---- plumbing -----------------------------------------------------------

    private void publish(String type, QueueEntry entry) {
        events.publish(KafkaTopicsConfig.QUEUE_EVENTS, type, entry.getId(), Map.of(
                "queueEntryId", entry.getId().toString(),
                "appointmentId", entry.getAppointmentId() == null ? "" : entry.getAppointmentId().toString(),
                "patientId", entry.getPatientId().toString(),
                "doctorId", entry.getDoctorId().toString(),
                "patientName", entry.getPatientName(),
                "doctorName", entry.getDoctorName(),
                "tokenNumber", entry.getTokenNumber(),
                "status", entry.getStatus().name(),
                "waitedMinutes", String.valueOf(entry.waitedMinutes())));
    }

    /**
     * Push to live screens only after the transaction commits — otherwise a
     * rolled-back change would flash on the waiting-room board.
     *
     * The broadcast payload is the *redacted* board snapshot, because the SSE
     * stream is unauthenticated (EventSource cannot send an Authorization
     * header). Privileged screens such as the doctor console treat this as a
     * "something changed" signal and re-fetch the full picture over an
     * authenticated request — so PHI never travels on the public channel.
     */
    private void broadcastAfterCommit(UUID doctorId) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    broadcaster.broadcast(doctorId, boardSnapshot(doctorId));
                }
            });
        } else {
            broadcaster.broadcast(doctorId, boardSnapshot(doctorId));
        }
    }
}
