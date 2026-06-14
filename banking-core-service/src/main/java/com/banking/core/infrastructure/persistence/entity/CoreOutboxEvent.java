package com.banking.core.infrastructure.persistence.entity;

import com.banking.shared.infrastructure.outbox.OutboxEvent;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "outbox_events", schema = "banking")
public class CoreOutboxEvent extends OutboxEvent {

    public CoreOutboxEvent() { super(); }

    public CoreOutboxEvent(UUID eventId, String aggregateType, UUID aggregateId,
                           String eventType, String payload) {
        super(eventId, aggregateType, aggregateId, eventType, payload);
    }
}
