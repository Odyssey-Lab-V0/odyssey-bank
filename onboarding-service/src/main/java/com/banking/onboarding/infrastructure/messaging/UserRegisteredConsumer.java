package com.banking.onboarding.infrastructure.messaging;

import com.banking.onboarding.application.command.OnboardingApplicationService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Listens to IAM's UserRegistered event and triggers customer onboarding.
 *
 * IAM publishes the event; Onboarding consumes it. No shared DB — only the Kafka message crosses the boundary.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class UserRegisteredConsumer {

    private final OnboardingApplicationService onboardingService;
    private final ObjectMapper objectMapper;

    @KafkaListener(
            topics = "banking.iam.user.registered.v1",
            groupId = "onboarding-service",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void onUserRegistered(ConsumerRecord<String, String> record) {
        log.info("[ONBOARDING] Received UserRegistered key={} offset={}", record.key(), record.offset());

        try {
            JsonNode payload = objectMapper.readTree(record.value());

            UUID userId    = UUID.fromString(payload.get("userId").asText());
            String email   = payload.get("email").asText();
            String firstName = payload.has("firstName") ? payload.get("firstName").asText() : "";
            String lastName  = payload.has("lastName")  ? payload.get("lastName").asText()  : "";

            onboardingService.initiateOnboarding(userId, email, firstName, lastName);

        } catch (Exception e) {
            log.error("[ONBOARDING] Failed to process UserRegistered: {}", e.getMessage(), e);
            // Do NOT rethrow — Spring Kafka will retry based on error handler config.
            // For now we log and move on; add a DLQ in production.
        }
    }
}
