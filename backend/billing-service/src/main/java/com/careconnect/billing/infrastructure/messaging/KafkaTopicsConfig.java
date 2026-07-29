package com.careconnect.billing.infrastructure.messaging;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaTopicsConfig {

    public static final String BILLING_EVENTS = "billing.events";

    @Bean
    NewTopic billingEvents() {
        return TopicBuilder.name(BILLING_EVENTS).partitions(3).replicas(1).build();
    }
}
