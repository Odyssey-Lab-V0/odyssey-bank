package com.banking.core.infrastructure.persistence.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Double-entry bookkeeping ledger row.
 * Every transaction creates two rows: one DEBIT, one CREDIT.
 * Balance = SUM(credit) - SUM(debit) per account — never stored, always derived.
 */
@Entity
@Table(name = "ledger_entries", schema = "banking")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class LedgerEntryJpaEntity {

    @Id
    @Column(name = "entry_id")
    private UUID entryId;

    @Column(name = "transaction_id", nullable = false)
    private UUID transactionId;

    @Column(name = "account_id", nullable = false)
    private UUID accountId;

    @Column(name = "entry_type", nullable = false, length = 6)
    private String entryType; // DEBIT or CREDIT

    @Column(name = "amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Column(name = "description")
    private String description;

    @Column(name = "posted_at", nullable = false, updatable = false)
    private Instant postedAt;
}
