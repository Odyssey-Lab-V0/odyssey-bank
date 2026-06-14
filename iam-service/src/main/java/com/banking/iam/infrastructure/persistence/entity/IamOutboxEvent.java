package com.banking.iam.infrastructure.persistence.entity;

import com.banking.shared.infrastructure.outbox.OutboxEvent;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.util.UUID;

/**
 * IAM-specific outbox event — maps to iam.outbox_events table.
 * Extends the shared OutboxEvent base which provides all columns.
 */
@Entity
@Table(schema = "iam", name = "outbox_events")
public class IamOutboxEvent extends OutboxEvent {

    public IamOutboxEvent(UUID eventId, String aggregateType, UUID aggregateId,
                          String eventType, String payload) {
        super(eventId, aggregateType, aggregateId, eventType, payload);
    }

    public IamOutboxEvent(UUID eventId, String aggregateType, UUID aggregateId,
                          String eventType, String payload, String traceId) {
        super(eventId, aggregateType, aggregateId, eventType, payload, traceId);
    }

    protected IamOutboxEvent() {
        super();
    }
}
