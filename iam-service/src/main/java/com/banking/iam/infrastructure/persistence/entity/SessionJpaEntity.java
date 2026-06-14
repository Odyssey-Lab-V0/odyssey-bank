package com.banking.iam.infrastructure.persistence.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "sessions", schema = "iam")
@Getter
@Setter
@NoArgsConstructor
public class SessionJpaEntity {

    @Id
    @Column(name = "session_id")
    private UUID sessionId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "refresh_token_hash", nullable = false, unique = true)
    private String refreshTokenHash;

    @Column(name = "device_fingerprint")
    private String deviceFingerprint;

    @Column(name = "ip_address")
    private String ipAddress;

    @Column(name = "user_agent")
    private String userAgent;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    // null = active session; non-null = revoked at that timestamp
    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public boolean isRevoked() {
        return revokedAt != null;
    }

    public void revoke() {
        this.revokedAt = Instant.now();
    }

    public static SessionJpaEntity create(UUID userId, String refreshTokenHash,
                                          String deviceFingerprint, String ipAddress,
                                          Instant expiresAt) {
        var s = new SessionJpaEntity();
        s.sessionId = UUID.randomUUID();
        s.userId = userId;
        s.refreshTokenHash = refreshTokenHash;
        s.deviceFingerprint = deviceFingerprint;
        s.ipAddress = ipAddress;
        s.expiresAt = expiresAt;
        s.createdAt = Instant.now();
        return s;
    }
}
