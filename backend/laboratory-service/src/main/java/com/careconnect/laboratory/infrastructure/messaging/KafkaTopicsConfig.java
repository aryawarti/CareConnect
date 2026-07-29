package com.careconnect.laboratory.infrastructure.messaging;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaTopicsConfig {
    public static final String LAB_EVENTS = "lab.events";

    @Bean
    NewTopic labEvents() {
        return TopicBuilder.name(LAB_EVENTS).partitions(3).replicas(1).build();
    }
}
