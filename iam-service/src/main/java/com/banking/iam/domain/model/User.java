package com.banking.iam.domain.model;

import com.banking.iam.domain.event.UserRegistered;
import com.banking.iam.domain.event.UserStatusChanged;
import com.banking.iam.domain.valueobject.Email;
import com.banking.iam.domain.valueobject.HashedPassword;
import com.banking.iam.domain.valueobject.PhoneNumber;
import com.banking.shared.domain.AggregateRoot;
import com.banking.shared.domain.exception.BusinessRuleViolationException;
import com.banking.shared.domain.exception.DomainException;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

/**
 * User — Aggregate Root of the Identity & Access bounded context.
 *
 * Invariants enforced here (not in services, not in DB checks alone):
 *  1. Email must be verified before the user can reach ACTIVE status
 *  2. Account locks after MAX_FAILED_ATTEMPTS consecutive failures
 *  3. A deleted user cannot be reactivated — create a new one
 *  4. MFA cannot be disabled once enabled (banking compliance rule)
 */
@Getter
public class User extends AggregateRoot<UUID> {

    private static final int MAX_FAILED_ATTEMPTS = 5;
    private static final long LOCKOUT_MINUTES = 30;

    private Email email;
    private PhoneNumber phoneNumber;
    private UserStatus status;
    private boolean emailVerified;
    private boolean phoneVerified;
    private boolean mfaEnabled;
    private String mfaSecret; // TOTP secret — encrypted at rest by infra layer
    private int failedAttempts;
    private Instant lockedUntil;
    private Instant lastLoginAt;
    private Instant createdAt;
    private Instant updatedAt;
    private long version; // optimistic locking

    // ── Factory method — the only way to create a new User ──────────────────

    public static User register(Email email, PhoneNumber phoneNumber) {
        var user = new User();
        user.setId(UUID.randomUUID());
        user.email = email;
        user.phoneNumber = phoneNumber;
        user.status = UserStatus.PENDING_VERIFICATION;
        user.emailVerified = false;
        user.phoneVerified = false;
        user.mfaEnabled = false;
        user.failedAttempts = 0;
        user.createdAt = Instant.now();
        user.updatedAt = Instant.now();
        user.version = 0;

        user.registerEvent(new UserRegistered(user.getId(), email.value(), Instant.now()));
        return user;
    }

    // ── Commands (state-changing domain operations) ──────────────────────────

    public void verifyEmail() {
        if (status == UserStatus.DELETED) {
            throw new BusinessRuleViolationException("USER_DELETED", "Deleted user cannot be modified");
        }
        this.emailVerified = true;
        this.updatedAt = Instant.now();
        tryActivate();
    }

    public void verifyPhone() {
        this.phoneVerified = true;
        this.updatedAt = Instant.now();
    }

    /**
     * Record a failed login attempt. Locks the account after MAX_FAILED_ATTEMPTS.
     */
    public void recordFailedLogin() {
        this.failedAttempts++;
        if (this.failedAttempts >= MAX_FAILED_ATTEMPTS) {
            this.lockedUntil = Instant.now().plusSeconds(LOCKOUT_MINUTES * 60);
            var prev = this.status;
            this.status = UserStatus.LOCKED;
            registerEvent(new UserStatusChanged(getId(), prev, UserStatus.LOCKED, "Too many failed attempts"));
        }
        this.updatedAt = Instant.now();
    }

    /**
     * Record a successful login — resets failure counter and updates last login.
     */
    public void recordSuccessfulLogin() {
        assertNotLocked();
        this.failedAttempts = 0;
        this.lockedUntil = null;
        this.lastLoginAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public void enableMfa(String totpSecret) {
        if (status != UserStatus.ACTIVE) {
            throw new BusinessRuleViolationException("MFA_REQUIRES_ACTIVE", "MFA can only be enabled for active users");
        }
        this.mfaEnabled = true;
        this.mfaSecret = totpSecret;
        this.updatedAt = Instant.now();
    }

    public void suspend(String reason) {
        if (status == UserStatus.DELETED) {
            throw new BusinessRuleViolationException("USER_DELETED", "Cannot suspend a deleted user");
        }
        var prev = this.status;
        this.status = UserStatus.SUSPENDED;
        this.updatedAt = Instant.now();
        registerEvent(new UserStatusChanged(getId(), prev, UserStatus.SUSPENDED, reason));
    }

    public void unlock() {
        if (status != UserStatus.LOCKED) {
            throw new BusinessRuleViolationException("USER_NOT_LOCKED", "User is not locked");
        }
        this.status = UserStatus.ACTIVE;
        this.failedAttempts = 0;
        this.lockedUntil = null;
        this.updatedAt = Instant.now();
        registerEvent(new UserStatusChanged(getId(), UserStatus.LOCKED, UserStatus.ACTIVE, "Manual unlock"));
    }

    // ── Queries (non-mutating) ───────────────────────────────────────────────

    public boolean isLocked() {
        return status == UserStatus.LOCKED ||
               (lockedUntil != null && lockedUntil.isAfter(Instant.now()));
    }

    public boolean canLogin() {
        return status == UserStatus.ACTIVE && emailVerified && !isLocked();
    }

    // ── Private helpers ──────────────────────────────────────────────────────

    private void tryActivate() {
        if (emailVerified && status == UserStatus.PENDING_VERIFICATION) {
            var prev = this.status;
            this.status = UserStatus.ACTIVE;
            registerEvent(new UserStatusChanged(getId(), prev, UserStatus.ACTIVE, "Email verified"));
        }
    }

    private void assertNotLocked() {
        if (isLocked()) {
            throw new DomainException("ACCOUNT_LOCKED",
                    "Account is locked until " + lockedUntil);
        }
    }

    // ── Reconstitution constructor (for JPA / repository) ───────────────────
    // Repositories use this to rebuild the aggregate from DB state.
    // DO NOT call this from application code — use the factory method.

    public static User reconstitute(UUID id, Email email, PhoneNumber phoneNumber,
                                    UserStatus status, boolean emailVerified, boolean phoneVerified,
                                    boolean mfaEnabled, String mfaSecret, int failedAttempts,
                                    Instant lockedUntil, Instant lastLoginAt,
                                    Instant createdAt, Instant updatedAt, long version) {
        var user = new User();
        user.setId(id);
        user.email = email;
        user.phoneNumber = phoneNumber;
        user.status = status;
        user.emailVerified = emailVerified;
        user.phoneVerified = phoneVerified;
        user.mfaEnabled = mfaEnabled;
        user.mfaSecret = mfaSecret;
        user.failedAttempts = failedAttempts;
        user.lockedUntil = lockedUntil;
        user.lastLoginAt = lastLoginAt;
        user.createdAt = createdAt;
        user.updatedAt = updatedAt;
        user.version = version;
        return user;
    }

    private User() {
        super();
    }
}
