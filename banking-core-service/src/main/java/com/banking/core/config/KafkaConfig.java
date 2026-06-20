package com.banking.core.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaConfig {

    @Bean
    public NewTopic accountOpenedTopic() {
        return TopicBuilder.name("banking.core.account.opened.v1")
                .partitions(1)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic transactionPostedTopic() {
        return TopicBuilder.name("banking.core.transaction.posted.v1")
                .partitions(1)
                .replicas(1)
                .build();
    }
}
