package com.banking.onboarding.infrastructure.persistence.entity;

import com.banking.shared.infrastructure.outbox.OutboxEvent;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "outbox_events", schema = "onboarding")
public class OnboardingOutboxEvent extends OutboxEvent {

    public OnboardingOutboxEvent() { super(); }

    public OnboardingOutboxEvent(UUID eventId, String aggregateType, UUID aggregateId,
                                  String eventType, String payload) {
        super(eventId, aggregateType, aggregateId, eventType, payload);
    }
}
