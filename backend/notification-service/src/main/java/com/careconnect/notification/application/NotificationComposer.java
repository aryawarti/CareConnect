package com.careconnect.notification.application;

import com.careconnect.notification.domain.Notification;
import java.util.Map;
import java.util.Optional;

/**
 * Turns a domain event into a templated notification. Pure function of the
 * event — trivially unit-testable, no I/O. Unknown event types return empty
 * (consumers must tolerate producers evolving; additive changes are free).
 */
public final class NotificationComposer {

    private NotificationComposer() { }

    public static Optional<Notification> compose(String eventType, String eventId,
                                                 Map<String, Object> payload) {
        String patient = str(payload, "patientName");
        String doctor = str(payload, "doctorName");
        String start = str(payload, "startAt");
        String recipient = str(payload, "patientId");

        return switch (eventType) {
            case "AppointmentRequested" -> Optional.of(new Notification(recipient,
                    "appointment-requested", "We received your appointment request",
                    "Hi %s, your request for an appointment with %s at %s was received and is awaiting confirmation."
                            .formatted(patient, doctor, start), eventId));
            case "AppointmentConfirmed" -> Optional.of(new Notification(recipient,
                    "appointment-confirmed", "Your appointment is confirmed",
                    "Hi %s, your appointment with %s at %s is confirmed. See you then!"
                            .formatted(patient, doctor, start), eventId));
            case "AppointmentCancelled" -> Optional.of(new Notification(recipient,
                    "appointment-cancelled", "Your appointment was cancelled",
                    "Hi %s, your appointment with %s at %s has been cancelled."
                            .formatted(patient, doctor, start), eventId));
            case "AppointmentCompleted" -> Optional.of(new Notification(recipient,
                    "appointment-completed", "Thanks for visiting",
                    "Hi %s, thank you for visiting %s. Your invoice will follow shortly."
                            .formatted(patient, doctor), eventId));
            case "InvoiceIssued" -> Optional.of(new Notification(recipient,
                    "invoice-issued", "Your invoice %s is ready".formatted(str(payload, "invoiceNumber")),
                    "Hi %s, invoice %s for your visit with %s is ready. Amount due: %s."
                            .formatted(patient, str(payload, "invoiceNumber"),
                                    doctor, str(payload, "amount")), eventId));
            case "InvoicePaid" -> Optional.of(new Notification(recipient,
                    "invoice-paid", "Payment received — thank you",
                    "Hi %s, we received your payment of %s for invoice %s."
                            .formatted(patient, str(payload, "amount"),
                                    str(payload, "invoiceNumber")), eventId));
            case "PatientRegistered" -> Optional.of(new Notification(str(payload, "patientId"),
                    "patient-welcome", "Welcome to CareConnect",
                    "Hi %s, your patient profile (%s) has been created."
                            .formatted(str(payload, "fullName"), str(payload, "patientNumber")), eventId));
            default -> Optional.empty();
        };
    }

    private static String str(Map<String, Object> payload, String key) {
        Object value = payload.get(key);
        return value == null ? "" : value.toString();
    }
}
