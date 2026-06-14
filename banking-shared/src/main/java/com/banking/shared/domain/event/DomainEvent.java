package com.banking.shared.domain.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Base interface for all domain events in the banking platform.
 *
 * Domain events represent facts that happened in the domain — they are immutable
 * and named in the past tense: UserRegistered, TransactionPosted, KYCApproved.
 *
 * They are NOT commands (intent) and NOT integration events (yet).
 * The Outbox relay converts domain events into Kafka integration events.
 */
public interface DomainEvent {

    /** Unique ID for this event occurrence — used for idempotency on the consumer side. */
    UUID eventId();

    /** The type name used as Kafka event type header. e.g. "UserRegistered" */
    String eventType();

    /** ID of the aggregate that raised this event. */
    UUID aggregateId();

    /** Type name of the aggregate. e.g. "User" */
    String aggregateType();

    /** When this event occurred in the domain. */
    Instant occurredAt();

    /** Schema version — increment when payload structure changes. */
    default int version() {
        return 1;
    }
}
