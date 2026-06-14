package com.banking.core.infrastructure.persistence.repository;

import com.banking.core.infrastructure.persistence.entity.LedgerEntryJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public interface LedgerEntryJpaRepository extends JpaRepository<LedgerEntryJpaEntity, UUID> {

    List<LedgerEntryJpaEntity> findByAccountIdOrderByPostedAtDesc(UUID accountId);

    List<LedgerEntryJpaEntity> findByTransactionId(UUID transactionId);

    @Query("""
            SELECT COALESCE(SUM(CASE WHEN e.entryType = 'CREDIT' THEN e.amount ELSE -e.amount END), 0)
            FROM LedgerEntryJpaEntity e WHERE e.accountId = :accountId
            """)
    BigDecimal computeBalance(@Param("accountId") UUID accountId);
}
