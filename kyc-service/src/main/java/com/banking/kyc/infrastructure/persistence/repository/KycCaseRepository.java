package com.banking.kyc.infrastructure.persistence.repository;

import com.banking.kyc.infrastructure.persistence.entity.KycCaseEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface KycCaseRepository extends JpaRepository<KycCaseEntity, UUID> {
    boolean existsByCustomerId(UUID customerId);
    Optional<KycCaseEntity> findByCustomerId(UUID customerId);
}
