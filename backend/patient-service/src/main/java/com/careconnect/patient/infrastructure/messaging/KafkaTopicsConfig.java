package com.careconnect.patient.infrastructure.messaging;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaTopicsConfig {

    public static final String PATIENT_EVENTS = "patient.events";

    @Bean
    NewTopic patientEvents() {
        return TopicBuilder.name(PATIENT_EVENTS).partitions(3).replicas(1).build();
    }
}
