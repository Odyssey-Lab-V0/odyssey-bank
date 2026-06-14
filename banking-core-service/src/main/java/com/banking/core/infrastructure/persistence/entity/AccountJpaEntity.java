package com.banking.core.infrastructure.persistence.entity;

import com.banking.core.domain.model.AccountStatus;
import com.banking.core.domain.model.AccountType;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "accounts", schema = "banking")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class AccountJpaEntity {

    @Id
    @Column(name = "account_id")
    private UUID accountId;

    @Column(name = "customer_id", nullable = false)
    private UUID customerId;

    @Column(name = "account_number", nullable = false, unique = true, length = 16)
    private String accountNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "account_type", nullable = false, length = 20)
    private AccountType accountType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private AccountStatus status;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Column(name = "balance", nullable = false, precision = 19, scale = 2)
    private BigDecimal balance;

    @Column(name = "daily_transaction_limit", nullable = false, precision = 19, scale = 2)
    private BigDecimal dailyTransactionLimit;

    @Column(name = "daily_transacted_today", nullable = false, precision = 19, scale = 2)
    private BigDecimal dailyTransactedToday;

    @Column(name = "daily_limit_reset_at")
    private Instant dailyLimitResetAt;

    @Column(name = "opened_at", nullable = false, updatable = false)
    private Instant openedAt;

    @Column(name = "closed_at")
    private Instant closedAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;
}
