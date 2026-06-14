package com.banking.iam.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/**
 * Declares Kafka topics. Spring Kafka auto-creates these on startup if they don't exist.
 *
 * Partitions: 3 — allows 3 parallel consumers per topic
 * Replicas: 1 — single broker in dev; set to 3 in production (one per AZ)
 */
@Configuration
public class KafkaConfig {

    @Bean
    public NewTopic userRegisteredTopic() {
        return TopicBuilder.name("banking.iam.user.registered.v1")
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic userStatusChangedTopic() {
        return TopicBuilder.name("banking.iam.user.status-changed.v1")
                .partitions(3)
                .replicas(1)
                .build();
    }
}
