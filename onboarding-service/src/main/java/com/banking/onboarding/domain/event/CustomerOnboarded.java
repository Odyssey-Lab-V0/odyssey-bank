package com.banking.onboarding.domain.event;

import com.banking.shared.domain.event.DomainEvent;

import java.time.Instant;
import java.util.UUID;

public record CustomerOnboarded(
        UUID eventId,
        UUID customerId,
        UUID userId,
        String email,
        String fullName,
        Instant occurredAt
) implements DomainEvent {

    @Override public UUID eventId()       { return eventId; }
    @Override public UUID aggregateId()   { return customerId; }
    @Override public String aggregateType() { return "Customer"; }
    @Override public String eventType()   { return "CustomerOnboarded"; }
    @Override public Instant occurredAt() { return occurredAt; }
}
