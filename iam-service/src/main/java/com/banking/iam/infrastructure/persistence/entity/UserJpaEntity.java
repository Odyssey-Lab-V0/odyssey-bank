package com.banking.iam.infrastructure.persistence.entity;

import com.banking.iam.domain.model.UserStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * JPA entity for iam.users table.
 *
 * This is an INFRASTRUCTURE concern — it lives in the infrastructure layer.
 * The domain model (User.java) is mapped TO and FROM this entity via UserMapper.
 *
 * Why separate domain model and JPA entity?
 *  - Domain model has rich behaviour (methods, invariants)
 *  - JPA entity is a data container optimised for ORM mapping
 *  - Mixing them couples your domain to JPA annotations and lazy loading
 */
@Entity
@Table(schema = "iam", name = "users")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserJpaEntity {

    @Id
    @Column(name = "user_id", updatable = false, nullable = false)
    private UUID userId;

    @Column(name = "email", nullable = false, unique = true, length = 254)
    private String email;

    @Column(name = "phone_number", length = 20)
    private String phoneNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private UserStatus status;

    @Column(name = "mfa_enabled", nullable = false)
    private boolean mfaEnabled;

    @Column(name = "mfa_secret")
    private String mfaSecret;

    @Column(name = "email_verified", nullable = false)
    private boolean emailVerified;

    @Column(name = "phone_verified", nullable = false)
    private boolean phoneVerified;

    @Column(name = "failed_attempts", nullable = false, columnDefinition = "smallint")
    private short failedAttempts;

    @Column(name = "locked_until")
    private Instant lockedUntil;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;
}
