package com.banking.core.domain.event;

import com.banking.shared.domain.event.DomainEvent;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record TransactionPosted(
        UUID eventId,
        UUID aggregateId,
        String transactionType,
        BigDecimal amount,
        String currency,
        String reference,
        Instant occurredAt
) implements DomainEvent {

    @Override public String eventType()     { return "TransactionPosted"; }
    @Override public String aggregateType() { return "Account"; }
}
