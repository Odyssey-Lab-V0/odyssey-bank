package com.banking.iam.domain.event;

import com.banking.iam.domain.model.UserStatus;
import com.banking.shared.domain.event.BaseDomainEvent;

import java.util.UUID;

/**
 * Raised whenever the User's status transitions (activated, locked, suspended, etc.)
 * Kafka topic: banking.iam.user.status-changed.v1
 */
public class UserStatusChanged extends BaseDomainEvent {

    private final UUID userId;
    private final UserStatus previousStatus;
    private final UserStatus newStatus;
    private final String reason;

    public UserStatusChanged(UUID userId, UserStatus previousStatus, UserStatus newStatus, String reason) {
        super();
        this.userId = userId;
        this.previousStatus = previousStatus;
        this.newStatus = newStatus;
        this.reason = reason;
    }

    @Override public UUID aggregateId()     { return userId; }
    @Override public String aggregateType() { return "User"; }
    @Override public String eventType()     { return "UserStatusChanged"; }

    public UUID getUserId()              { return userId; }
    public UserStatus getPreviousStatus() { return previousStatus; }
    public UserStatus getNewStatus()      { return newStatus; }
    public String getReason()            { return reason; }
}
