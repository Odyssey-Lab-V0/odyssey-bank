package com.banking.core.domain.event;

import com.banking.shared.domain.event.DomainEvent;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record AccountOpened(
        UUID eventId,
        UUID aggregateId,
        String accountNumber,
        String accountType,
        String currency,
        BigDecimal initialDeposit,
        Instant occurredAt
) implements DomainEvent {

    @Override public String eventType()     { return "AccountOpened"; }
    @Override public String aggregateType() { return "Account"; }
}
