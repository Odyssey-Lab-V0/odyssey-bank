package com.banking.iam.infrastructure.persistence.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(schema = "iam", name = "credentials")
@Getter
@NoArgsConstructor
public class CredentialJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "credential_id", updatable = false)
    private UUID credentialId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "algorithm", nullable = false, length = 20)
    private String algorithm = "BCRYPT";

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    public CredentialJpaEntity(UUID userId, String passwordHash) {
        this.userId = userId;
        this.passwordHash = passwordHash;
        this.algorithm = "BCRYPT";
        this.createdAt = Instant.now();
        this.active = true;
    }
}
