package com.banking.shared.domain.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Concrete base for domain events. Subclass this for each event in a bounded context.
 *
 * Example:
 *   public record UserRegistered(UUID aggregateId, String email, Instant occurredAt)
 *       extends BaseDomainEvent { ... }
 */
public abstract class BaseDomainEvent implements DomainEvent {

    private final UUID eventId;
    private final Instant occurredAt;

    protected BaseDomainEvent() {
        this.eventId = UUID.randomUUID();
        this.occurredAt = Instant.now();
    }

    @Override
    public UUID eventId() {
        return eventId;
    }

    @Override
    public Instant occurredAt() {
        return occurredAt;
    }
}
