package com.banking.aml.infrastructure.persistence.repository;

import com.banking.aml.infrastructure.persistence.entity.AmlAlertEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AmlAlertRepository extends JpaRepository<AmlAlertEntity, UUID> {
    List<AmlAlertEntity> findByAccountIdOrderByCreatedAtDesc(UUID accountId);
}
