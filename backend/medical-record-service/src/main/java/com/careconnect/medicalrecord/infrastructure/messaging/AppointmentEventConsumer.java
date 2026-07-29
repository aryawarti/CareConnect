package com.careconnect.medicalrecord.infrastructure.messaging;

import com.careconnect.medicalrecord.application.MedicalRecordService;
import com.careconnect.medicalrecord.domain.ProcessedEvent;
import com.careconnect.medicalrecord.infrastructure.repository.ProcessedEventRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Opens an encounter when a visit actually happens (AppointmentCompleted).
 *
 * Two independent idempotency guards, deliberately:
 *  1. processed_events (this consumer already saw this eventId), and
 *  2. the unique appointment_id on encounters (this visit already has a chart)
 * The second covers redelivery of a *different* event id for the same
 * appointment — e.g. a producer replay after the outbox lands in Phase 9.
 */
@Component
public class AppointmentEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(AppointmentEventConsumer.class);

    private final MedicalRecordService records;
    private final ProcessedEventRepository processed;
    private final ObjectMapper objectMapper;

    public AppointmentEventConsumer(MedicalRecordService records,
                                    ProcessedEventRepository processed,
                                    ObjectMapper objectMapper) {
        this.records = records;
        this.processed = processed;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = "appointment.events", groupId = "medical-record-service")
    @Transactional
    public void onAppointmentEvent(String message) throws Exception {
        Map<String, Object> envelope = objectMapper.readValue(message, new TypeReference<>() { });
        String eventId = (String) envelope.get("eventId");
        String eventType = (String) envelope.get("eventType");

        if (eventId == null || processed.existsById(eventId)) {
            return;
        }
        if ("AppointmentCompleted".equals(eventType)) {
            @SuppressWarnings("unchecked")
            Map<String, Object> payload = (Map<String, Object>) envelope.getOrDefault("payload", Map.of());
            records.openFromCompletedAppointment(
                    UUID.fromString((String) payload.get("appointmentId")),
                    UUID.fromString((String) payload.get("patientId")),
                    UUID.fromString((String) payload.get("doctorId")),
                    (String) payload.get("patientName"),
                    (String) payload.get("doctorName"),
                    Instant.parse((String) payload.get("startAt")));
            log.info("encounter opened from event {}", eventId);
        }
        // Other appointment events are ignored — a consumer subscribes to a
        // topic, not to a curated feed; ignoring unknown types is normal.
        processed.save(new ProcessedEvent(eventId, "appointment.events"));
    }
}
