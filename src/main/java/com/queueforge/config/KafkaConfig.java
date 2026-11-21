package com.queueforge.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaConfig {

    public static final String TOPIC_NAME = "queueforge.job-events";

    @Bean
    public NewTopic jobEventsTopic() {
        return TopicBuilder.name(TOPIC_NAME)
                .partitions(3) // Allow 3 consumers to read in parallel
                .replicas(1)   // Local dev only has 1 Kafka node
                .build();
    }
}