package com.banking.kyc.infrastructure.messaging;

import com.banking.kyc.application.KycApplicationService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class CustomerOnboardedConsumer {

    private final KycApplicationService kycService;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "banking.onboarding.customer.onboarded.v1", groupId = "kyc-service")
    public void onCustomerOnboarded(ConsumerRecord<String, String> record) {
        log.info("[KYC] Received CustomerOnboarded key={}", record.key());
        try {
            JsonNode p = objectMapper.readTree(record.value());
            UUID customerId = UUID.fromString(p.get("customerId").asText());
            UUID userId     = UUID.fromString(p.get("userId").asText());
            String email    = p.get("email").asText();
            String fullName = p.has("fullName") ? p.get("fullName").asText() : "";

            kycService.initiateKyc(customerId, userId, email, fullName);
        } catch (Exception e) {
            log.error("[KYC] Failed to process CustomerOnboarded: {}", e.getMessage(), e);
        }
    }
}
