package com.careconnect.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.careconnect.notification.infrastructure.repository.NotificationRepository;

/**
 * The at-least-once contract test: the same event delivered twice must
 * produce exactly one notification. Embedded Kafka broker + real Postgres.
 */
@Testcontainers(disabledWithoutDocker = true)
@EmbeddedKafka(partitions = 1, topics = {"appointment.events", "patient.events"})
@SpringBootTest(properties = {
        "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
        "spring.kafka.consumer.auto-offset-reset=earliest",
        "spring.kafka.consumer.key-deserializer=org.apache.kafka.common.serialization.StringDeserializer",
        "spring.kafka.consumer.value-deserializer=org.apache.kafka.common.serialization.StringDeserializer",
        "spring.kafka.producer.key-serializer=org.apache.kafka.common.serialization.StringSerializer",
        "spring.kafka.producer.value-serializer=org.apache.kafka.common.serialization.StringSerializer"
})
class NotificationIdempotencyTest {

    @TestConfiguration
    static class Containers {
        @Bean
        @ServiceConnection
        PostgreSQLContainer<?> postgres() {
            return new PostgreSQLContainer<>("postgres:16-alpine");
        }
    }

    @Autowired KafkaTemplate<String, String> kafka;
    @Autowired NotificationRepository notifications;

    @Test
    void duplicateEventProducesExactlyOneNotification() {
        String eventId = UUID.randomUUID().toString();
        String envelope = """
                {"eventId":"%s","eventType":"AppointmentConfirmed","occurredAt":"2026-07-19T10:00:00Z",
                 "aggregateId":"a1","version":1,"correlationId":"c1",
                 "payload":{"patientId":"p1","patientName":"Asha Verma","doctorName":"Dr. Rao",
                            "startAt":"2026-07-20T10:00:00Z"}}""".formatted(eventId);

        long before = notifications.count();
        kafka.send("appointment.events", "a1", envelope);
        kafka.send("appointment.events", "a1", envelope);   // redelivery simulation

        await().atMost(Duration.ofSeconds(15)).untilAsserted(() ->
                assertThat(notifications.count()).isEqualTo(before + 1));
        // settle time: a second (duplicate) processing would add another row
        try { Thread.sleep(2000); } catch (InterruptedException ignored) { }
        assertThat(notifications.count()).isEqualTo(before + 1);
    }
}
