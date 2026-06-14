package com.banking.shared.domain;

import com.banking.shared.domain.event.DomainEvent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Base class for all Aggregate Roots in the banking domain.
 *
 * An Aggregate Root is the sole entry point into an aggregate — all mutations
 * go through it, never directly to child entities. It accumulates domain events
 * during a business operation; the application layer drains and publishes them
 * after the transaction commits (via the Outbox pattern).
 *
 * Usage:
 *   public class Account extends AggregateRoot<AccountId> { ... }
 */
public abstract class AggregateRoot<ID> extends Entity<ID> {

    private final List<DomainEvent> domainEvents = new ArrayList<>();

    protected AggregateRoot(ID id) {
        super(id);
    }

    protected AggregateRoot() {
        super();
    }

    /** Record an event that occurred within this aggregate during this operation. */
    protected void registerEvent(DomainEvent event) {
        domainEvents.add(event);
    }

    /** Called by the application layer after persisting. Events go to the Outbox. */
    public List<DomainEvent> pullDomainEvents() {
        List<DomainEvent> events = new ArrayList<>(domainEvents);
        domainEvents.clear();
        return Collections.unmodifiableList(events);
    }

    public List<DomainEvent> peekDomainEvents() {
        return Collections.unmodifiableList(domainEvents);
    }
}
