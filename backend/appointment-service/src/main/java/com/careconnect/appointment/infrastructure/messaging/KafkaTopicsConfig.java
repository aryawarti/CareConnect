package com.careconnect.appointment.infrastructure.messaging;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/** Producers own their topics' definitions (auto-create is off in the broker). */
@Configuration
public class KafkaTopicsConfig {

    public static final String APPOINTMENT_EVENTS = "appointment.events";

    @Bean
    NewTopic appointmentEvents() {
        return TopicBuilder.name(APPOINTMENT_EVENTS).partitions(3).replicas(1).build();
    }
}
