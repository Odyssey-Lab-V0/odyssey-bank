package com.banking.notification.infrastructure.messaging;

import com.banking.notification.application.NotificationService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Listens to events from IAM, Onboarding, and Banking Core.
 * Each consumer group is unique so Notification gets its own independent offset.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class NotificationEventConsumer {

    private final NotificationService notificationService;
    private final ObjectMapper objectMapper;

    // ── IAM: user registered ────────────────────────────────────────────────

    @KafkaListener(topics = "banking.iam.user.registered.v1", groupId = "notification-service")
    public void onUserRegistered(ConsumerRecord<String, String> record) {
        try {
            JsonNode p = objectMapper.readTree(record.value());
            UUID userId = UUID.fromString(p.get("userId").asText());
            String email = p.get("email").asText();

            notificationService.send(userId, email,
                    "Welcome to Odyssey Bank — verify your account",
                    "Hi there,\n\nYour Odyssey Bank account has been created. " +
                    "Complete onboarding to start banking.\n\nTeam Odyssey",
                    "UserRegistered");
        } catch (Exception e) {
            log.error("[NOTIFICATION] Failed processing UserRegistered: {}", e.getMessage());
        }
    }

    // ── Onboarding: customer onboarded ──────────────────────────────────────

    @KafkaListener(topics = "banking.onboarding.customer.onboarded.v1", groupId = "notification-service")
    public void onCustomerOnboarded(ConsumerRecord<String, String> record) {
        try {
            JsonNode p = objectMapper.readTree(record.value());
            UUID customerId = UUID.fromString(p.get("customerId").asText());
            String email    = p.get("email").asText();
            String fullName = p.has("fullName") ? p.get("fullName").asText() : "Valued Customer";

            notificationService.send(customerId, email,
                    "Your Odyssey Bank profile is ready",
                    "Hi " + fullName + ",\n\nYour customer profile has been created. " +
                    "KYC verification will begin shortly.\n\nTeam Odyssey",
                    "CustomerOnboarded");
        } catch (Exception e) {
            log.error("[NOTIFICATION] Failed processing CustomerOnboarded: {}", e.getMessage());
        }
    }

    // ── Banking Core: account opened ────────────────────────────────────────

    @KafkaListener(topics = "banking.core.account.opened.v1", groupId = "notification-service")
    public void onAccountOpened(ConsumerRecord<String, String> record) {
        try {
            JsonNode p = objectMapper.readTree(record.value());
            UUID customerId   = UUID.fromString(p.get("customerId").asText());
            String accountNo  = p.has("accountNumber") ? p.get("accountNumber").asText() : "N/A";
            String type       = p.has("accountType")   ? p.get("accountType").asText()   : "account";
            // No email in this event — log only (would need customer lookup in real system)
            log.info("📧 [NOTIFICATION] AccountOpened customerId={} accountNo={} type={} — email lookup skipped in dev",
                    customerId, accountNo, type);
        } catch (Exception e) {
            log.error("[NOTIFICATION] Failed processing AccountOpened: {}", e.getMessage());
        }
    }

    // ── Banking Core: transaction posted ────────────────────────────────────

    @KafkaListener(topics = "banking.core.transaction.posted.v1", groupId = "notification-service")
    public void onTransactionPosted(ConsumerRecord<String, String> record) {
        try {
            JsonNode p = objectMapper.readTree(record.value());
            String accountId = p.has("accountId") ? p.get("accountId").asText() : "unknown";
            String amount    = p.has("amount")    ? p.get("amount").asText()    : "0";
            String txType    = p.has("type")      ? p.get("type").asText()      : "TRANSACTION";

            log.info("📧 [NOTIFICATION] TransactionPosted accountId={} amount={} type={} — alert logged",
                    accountId, amount, txType);
        } catch (Exception e) {
            log.error("[NOTIFICATION] Failed processing TransactionPosted: {}", e.getMessage());
        }
    }
}
