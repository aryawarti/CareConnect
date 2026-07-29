package com.careconnect.queue.infrastructure.messaging;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaTopicsConfig {

    public static final String QUEUE_EVENTS = "queue.events";

    @Bean
    NewTopic queueEvents() {
        return TopicBuilder.name(QUEUE_EVENTS).partitions(3).replicas(1).build();
    }
}
