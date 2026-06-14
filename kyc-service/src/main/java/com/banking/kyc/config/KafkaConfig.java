package com.banking.kyc.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaConfig {

    @Bean
    public NewTopic kycApprovedTopic() {
        return TopicBuilder.name("banking.kyc.approved.v1").partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic kycRejectedTopic() {
        return TopicBuilder.name("banking.kyc.rejected.v1").partitions(3).replicas(1).build();
    }
}
