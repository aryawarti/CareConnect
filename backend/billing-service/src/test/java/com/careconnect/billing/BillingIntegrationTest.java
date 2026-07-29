package com.careconnect.billing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

import com.careconnect.billing.application.BillingService;
import com.careconnect.billing.domain.Invoice;
import com.careconnect.billing.domain.InvoiceStatus;
import com.careconnect.billing.infrastructure.repository.InvoiceRepository;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.data.domain.PageRequest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.security.access.AccessDeniedException;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;

/** appointment.events -> invoice, and the payment rules, end to end. */
@Testcontainers(disabledWithoutDocker = true)
@EmbeddedKafka(partitions = 1, topics = {"appointment.events", "billing.events"})
@SpringBootTest(properties = {
        "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
        "spring.kafka.consumer.auto-offset-reset=earliest",
        "spring.kafka.consumer.key-deserializer=org.apache.kafka.common.serialization.StringDeserializer",
        "spring.kafka.consumer.value-deserializer=org.apache.kafka.common.serialization.StringDeserializer",
        "spring.kafka.producer.key-serializer=org.apache.kafka.common.serialization.StringSerializer",
        "spring.kafka.producer.value-serializer=org.apache.kafka.common.serialization.StringSerializer"
})
class BillingIntegrationTest {

    @TestConfiguration
    static class Containers {
        @Bean
        @ServiceConnection
        PostgreSQLContainer<?> postgres() {
            return new PostgreSQLContainer<>("postgres:16-alpine");
        }
    }

    @Autowired KafkaTemplate<String, String> kafka;
    @Autowired InvoiceRepository invoices;
    @Autowired BillingService billing;

    private String completedEvent(String eventId, UUID appointmentId, UUID patientId, String fee) {
        return """
                {"eventId":"%s","eventType":"AppointmentCompleted","occurredAt":"2026-07-20T10:00:00Z",
                 "aggregateId":"%s","version":1,"correlationId":"c1",
                 "payload":{"appointmentId":"%s","patientId":"%s","doctorId":"%s",
                            "patientName":"Asha Verma","doctorName":"Dr. Rao",
                            "startAt":"2026-07-20T10:00:00Z","endAt":"2026-07-20T10:30:00Z",
                            "fee":"%s","status":"COMPLETED"}}"""
                .formatted(eventId, appointmentId, appointmentId, patientId,
                        UUID.randomUUID(), fee);
    }

    @Test
    void completedAppointmentGeneratesExactlyOneInvoiceWithTheSnapshottedFee() {
        UUID appointment = UUID.randomUUID();
        UUID patient = UUID.randomUUID();

        kafka.send("appointment.events", appointment.toString(),
                completedEvent(UUID.randomUUID().toString(), appointment, patient, "800.00"));

        await().atMost(Duration.ofSeconds(20)).untilAsserted(() ->
                assertThat(invoices.existsByAppointmentId(appointment)).isTrue());

        Invoice invoice = invoices.findByAppointmentId(appointment).orElseThrow();
        assertThat(invoice.getAmount()).isEqualByComparingTo("800.00");
        assertThat(invoice.getInvoiceNumber()).matches("INV-\\d{6}");
        assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.ISSUED);

        // replay with a different event id must not double-bill the visit
        kafka.send("appointment.events", appointment.toString(),
                completedEvent(UUID.randomUUID().toString(), appointment, patient, "800.00"));
        try {
            Thread.sleep(3000);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
        assertThat(invoices.findByPatientIdOrderByIssuedAtDesc(patient, PageRequest.of(0, 10))
                .getTotalElements()).isEqualTo(1);
    }

    @Test
    void paymentSettlesTheInvoiceAndIsOwnershipChecked() {
        UUID patient = UUID.randomUUID();
        Invoice invoice = billing.issueForCompletedAppointment(UUID.randomUUID(), patient,
                UUID.randomUUID(), "Asha Verma", "Dr. Rao", new BigDecimal("650.00"));

        // another patient cannot pay (or even see) someone else's invoice
        assertThatThrownBy(() -> billing.pay(invoice.getId(), UUID.randomUUID(), false,
                new BigDecimal("650.00"), "SIMULATED", "ref-x"))
                .isInstanceOf(AccessDeniedException.class);

        Invoice paid = billing.pay(invoice.getId(), patient, false,
                new BigDecimal("650.00"), "SIMULATED", "ref-" + UUID.randomUUID());
        assertThat(paid.getStatus()).isEqualTo(InvoiceStatus.PAID);
        assertThat(paid.getPayments()).hasSize(1);
    }
}
