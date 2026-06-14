package com.banking.iam.infrastructure.persistence.repository;

import com.banking.iam.infrastructure.persistence.entity.CredentialJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface CredentialJpaRepository extends JpaRepository<CredentialJpaEntity, UUID> {
    Optional<CredentialJpaEntity> findByUserIdAndActiveTrue(UUID userId);
}
