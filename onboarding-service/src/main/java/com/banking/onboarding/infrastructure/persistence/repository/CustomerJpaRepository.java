package com.banking.onboarding.infrastructure.persistence.repository;

import com.banking.onboarding.infrastructure.persistence.entity.CustomerJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface CustomerJpaRepository extends JpaRepository<CustomerJpaEntity, UUID> {
    Optional<CustomerJpaEntity> findByUserId(UUID userId);
    boolean existsByUserId(UUID userId);
}
