package com.banking.iam.infrastructure.messaging;

import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Self-verification consumer — IAM listens to its own events to confirm
 * the full publish → consume cycle works.
 *
 * In production this would be in downstream services (Onboarding, Notification, Audit).
 * Consumer group: iam-service-self — isolated so it doesn't interfere with real consumers.
 */
@Component
@Slf4j
public class IamEventConsumer {

    @KafkaListener(
            topics = "banking.iam.user.registered.v1",
            groupId = "iam-service-self",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void onUserRegistered(ConsumerRecord<String, String> record) {
        log.info("[KAFKA ✓] Received UserRegistered — key={}, partition={}, offset={}, payload={}",
                record.key(), record.partition(), record.offset(), record.value());
    }

    @KafkaListener(
            topics = "banking.iam.user.status-changed.v1",
            groupId = "iam-service-self",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void onUserStatusChanged(ConsumerRecord<String, String> record) {
        log.info("[KAFKA ✓] Received UserStatusChanged — key={}, partition={}, offset={}, payload={}",
                record.key(), record.partition(), record.offset(), record.value());
    }
}
