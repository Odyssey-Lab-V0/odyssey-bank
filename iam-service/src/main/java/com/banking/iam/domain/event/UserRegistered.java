package com.banking.iam.domain.event;

import com.banking.shared.domain.event.BaseDomainEvent;

import java.time.Instant;
import java.util.UUID;

/**
 * Raised when a new User completes registration.
 *
 * Consumers:
 *   - Onboarding Service: creates an OnboardingApplication
 *   - Notification Service: sends welcome email
 *   - Audit Service: records the event
 *
 * Kafka topic: banking.iam.user.registered.v1
 */
public class UserRegistered extends BaseDomainEvent {

    private final UUID userId;
    private final String email;
    private final Instant registeredAt;

    public UserRegistered(UUID userId, String email, Instant registeredAt) {
        super();
        this.userId = userId;
        this.email = email;
        this.registeredAt = registeredAt;
    }

    @Override public UUID aggregateId()   { return userId; }
    @Override public String aggregateType() { return "User"; }
    @Override public String eventType()   { return "UserRegistered"; }

    public UUID getUserId()          { return userId; }
    public String getEmail()         { return email; }
    public Instant getRegisteredAt() { return registeredAt; }
}
