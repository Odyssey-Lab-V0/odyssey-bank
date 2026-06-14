package com.banking.core.infrastructure.persistence.repository;

import com.banking.core.infrastructure.persistence.entity.AccountJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AccountJpaRepository extends JpaRepository<AccountJpaEntity, UUID> {
    Optional<AccountJpaEntity> findByAccountNumber(String accountNumber);
    List<AccountJpaEntity> findByCustomerId(UUID customerId);
}
