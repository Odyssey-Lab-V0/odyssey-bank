package com.banking.iam.infrastructure.persistence.repository;

import com.banking.iam.infrastructure.persistence.entity.SessionJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface SessionJpaRepository extends JpaRepository<SessionJpaEntity, UUID> {

    Optional<SessionJpaEntity> findByRefreshTokenHash(String hash);

    @Modifying
    @Query("UPDATE SessionJpaEntity s SET s.revokedAt = :now WHERE s.userId = :userId AND s.revokedAt IS NULL")
    void revokeAllForUser(@Param("userId") UUID userId, @Param("now") Instant now);

    @Modifying
    @Query("DELETE FROM SessionJpaEntity s WHERE s.expiresAt < :before OR s.revokedAt IS NOT NULL")
    void deleteExpiredAndRevoked(@Param("before") Instant before);
}
