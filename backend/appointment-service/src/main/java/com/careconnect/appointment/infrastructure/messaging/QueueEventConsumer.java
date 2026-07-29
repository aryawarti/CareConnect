package com.careconnect.appointment.infrastructure.messaging;

import com.careconnect.appointment.application.AppointmentService;
import com.careconnect.appointment.domain.InvalidTransitionException;
import com.careconnect.appointment.infrastructure.repository.ProcessedEventRepository;
import com.careconnect.appointment.domain.ProcessedEvent;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Closes the loop with Live Care Flow: when a doctor ends a consultation in
 * the queue, the appointment is completed here — which publishes
 * AppointmentCompleted and thereby opens the medical record, issues the
 * invoice and notifies the patient.
 *
 * Note the shape: queue-service does NOT call appointment-service to command
 * it. It states a fact ("this consultation finished"), and this context
 * decides what that means for an appointment (ADR-004).
 */
@Component
public class QueueEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(QueueEventConsumer.class);

    private final AppointmentService appointments;
    private final ProcessedEventRepository processed;
    private final ObjectMapper objectMapper;

    public QueueEventConsumer(AppointmentService appointments,
                              ProcessedEventRepository processed, ObjectMapper objectMapper) {
        this.appointments = appointments;
        this.processed = processed;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = "queue.events", groupId = "appointment-service")
    @Transactional
    public void onQueueEvent(String message) throws Exception {
        Map<String, Object> envelope = objectMapper.readValue(message, new TypeReference<>() { });
        String eventId = (String) envelope.get("eventId");
        String eventType = (String) envelope.get("eventType");

        if (eventId == null || processed.existsById(eventId)) {
            return;
        }
        if ("ConsultationCompleted".equals(eventType)) {
            @SuppressWarnings("unchecked")
            Map<String, Object> payload = (Map<String, Object>) envelope.getOrDefault("payload", Map.of());
            String appointmentId = (String) payload.get("appointmentId");
            if (appointmentId != null && !appointmentId.isBlank()) {
                try {
                    appointments.completeFromConsultation(UUID.fromString(appointmentId));
                    log.info("appointment {} completed from queue consultation", appointmentId);
                } catch (InvalidTransitionException e) {
                    // Already completed, or cancelled meanwhile: not an error,
                    // just a race we tolerate rather than retry forever.
                    log.info("skipping completion for {}: {}", appointmentId, e.getMessage());
                }
            }
            // Walk-ins have no appointmentId: the consultation is real, but
            // there is nothing to complete. Billing for walk-ins is future work.
        }
        processed.save(new ProcessedEvent(eventId, "queue.events"));
    }
}
