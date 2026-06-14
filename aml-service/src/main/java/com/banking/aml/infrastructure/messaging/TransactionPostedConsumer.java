package com.banking.aml.infrastructure.messaging;

import com.banking.aml.application.AmlScreeningService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class TransactionPostedConsumer {

    private final AmlScreeningService screeningService;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "banking.core.transaction.posted.v1", groupId = "aml-service")
    public void onTransactionPosted(ConsumerRecord<String, String> record) {
        log.debug("[AML] Screening transaction key={}", record.key());
        try {
            JsonNode p = objectMapper.readTree(record.value());

            UUID accountId     = UUID.fromString(p.get("accountId").asText());
            UUID transactionId = p.has("transactionId")
                    ? UUID.fromString(p.get("transactionId").asText())
                    : UUID.randomUUID();
            BigDecimal amount  = p.has("amount")
                    ? new BigDecimal(p.get("amount").asText())
                    : BigDecimal.ZERO;
            String txType      = p.has("type") ? p.get("type").asText() : "UNKNOWN";

            screeningService.screen(accountId, transactionId, amount, txType);
        } catch (Exception e) {
            log.error("[AML] Failed to process TransactionPosted: {}", e.getMessage(), e);
        }
    }
}
