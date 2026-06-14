package com.banking.kyc.infrastructure.persistence.entity;

import com.banking.shared.infrastructure.outbox.OutboxEvent;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "kyc_outbox_events", schema = "kyc_aml")
public class KycOutboxEvent extends OutboxEvent {

    public KycOutboxEvent() { super(); }

    public KycOutboxEvent(UUID eventId, String aggregateType, UUID aggregateId,
                          String eventType, String payload) {
        super(eventId, aggregateType, aggregateId, eventType, payload);
    }
}
