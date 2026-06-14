package com.banking.core.domain.model;

import com.banking.core.domain.event.AccountOpened;
import com.banking.core.domain.event.TransactionPosted;
import com.banking.core.domain.valueobject.AccountNumber;
import com.banking.core.domain.valueobject.Money;
import com.banking.shared.domain.AggregateRoot;
import com.banking.shared.domain.exception.BusinessRuleViolationException;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Account — Aggregate Root of the Banking Core bounded context.
 *
 * Invariants:
 *  1. Balance never goes below zero (no overdraft unless type allows it)
 *  2. Only ACTIVE accounts can post transactions
 *  3. Daily transaction limit enforced at domain level
 *  4. Balance is derived from ledger entries — this field is a cached snapshot
 *     updated atomically with each transaction (consistency without full ledger scan)
 */
@Getter
public class Account extends AggregateRoot<UUID> {

    private static final Money DAILY_LIMIT_DEFAULT = Money.of("50000.00", "USD");

    private UUID customerId;
    private AccountNumber accountNumber;
    private AccountType accountType;
    private AccountStatus status;
    private Money balance;
    private Money dailyTransactionLimit;
    private Money dailyTransactedToday;
    private Instant dailyLimitResetAt;
    private String currency;
    private Instant openedAt;
    private Instant closedAt;
    private Instant updatedAt;
    private long version;

    // ── Factory ─────────────────────────────────────────────────────────────

    public static Account open(UUID customerId, AccountType type, String currency, Money initialDeposit) {
        var account = new Account();
        account.setId(UUID.randomUUID());
        account.customerId = customerId;
        account.accountNumber = AccountNumber.generate();
        account.accountType = type;
        account.status = AccountStatus.ACTIVE;
        account.currency = currency;
        account.balance = initialDeposit != null ? initialDeposit : Money.zero(currency);
        account.dailyTransactionLimit = DAILY_LIMIT_DEFAULT;
        account.dailyTransactedToday = Money.zero(currency);
        account.dailyLimitResetAt = Instant.now().plusSeconds(86400);
        account.openedAt = Instant.now();
        account.updatedAt = Instant.now();
        account.version = 0;

        account.registerEvent(new AccountOpened(
                UUID.randomUUID(), account.getId(), account.accountNumber.value(),
                type.name(), currency,
                account.balance.amount(), Instant.now()));
        return account;
    }

    // ── Commands ─────────────────────────────────────────────────────────────

    public void credit(Money amount, String reference) {
        assertActive();
        this.balance = this.balance.add(amount);
        this.updatedAt = Instant.now();
        registerEvent(new TransactionPosted(
                UUID.randomUUID(), getId(), "CREDIT",
                amount.amount(), currency, reference, Instant.now()));

    }

    public void debit(Money amount, String reference) {
        assertActive();
        assertSufficientFunds(amount);
        assertWithinDailyLimit(amount);

        this.balance = this.balance.subtract(amount);
        resetDailyLimitIfNeeded();
        this.dailyTransactedToday = this.dailyTransactedToday.add(amount);
        this.updatedAt = Instant.now();

        registerEvent(new TransactionPosted(
                UUID.randomUUID(), getId(), "DEBIT",
                amount.amount(), currency, reference, Instant.now()));

    }

    public void suspend(String reason) {
        if (status == AccountStatus.CLOSED) {
            throw new BusinessRuleViolationException("ACCOUNT_CLOSED", "Cannot suspend a closed account");
        }
        this.status = AccountStatus.SUSPENDED;
        this.updatedAt = Instant.now();
    }

    public void close() {
        if (!balance.isZero()) {
            throw new BusinessRuleViolationException("NON_ZERO_BALANCE",
                    "Cannot close account with non-zero balance: " + balance);
        }
        this.status = AccountStatus.CLOSED;
        this.closedAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    // ── Private guards ───────────────────────────────────────────────────────

    private void assertActive() {
        if (status != AccountStatus.ACTIVE) {
            throw new BusinessRuleViolationException("ACCOUNT_NOT_ACTIVE",
                    "Account " + accountNumber.value() + " is " + status);
        }
    }

    private void assertSufficientFunds(Money amount) {
        if (balance.subtract(amount).isNegative()) {
            throw new BusinessRuleViolationException("INSUFFICIENT_FUNDS",
                    "Balance " + balance + " insufficient for debit of " + amount);
        }
    }

    private void assertWithinDailyLimit(Money amount) {
        resetDailyLimitIfNeeded();
        if (dailyTransactedToday.add(amount).isGreaterThan(dailyTransactionLimit)) {
            throw new BusinessRuleViolationException("DAILY_LIMIT_EXCEEDED",
                    "Daily transaction limit of " + dailyTransactionLimit + " would be exceeded");
        }
    }

    private void resetDailyLimitIfNeeded() {
        if (Instant.now().isAfter(dailyLimitResetAt)) {
            this.dailyTransactedToday = Money.zero(currency);
            this.dailyLimitResetAt = Instant.now().plusSeconds(86400);
        }
    }

    // ── Reconstitution ───────────────────────────────────────────────────────

    public static Account reconstitute(UUID id, UUID customerId, AccountNumber accountNumber,
                                       AccountType type, AccountStatus status, Money balance,
                                       Money dailyLimit, Money dailyTransactedToday,
                                       Instant dailyLimitResetAt, String currency,
                                       Instant openedAt, Instant closedAt,
                                       Instant updatedAt, long version) {
        var a = new Account();
        a.setId(id);
        a.customerId = customerId;
        a.accountNumber = accountNumber;
        a.accountType = type;
        a.status = status;
        a.balance = balance;
        a.dailyTransactionLimit = dailyLimit;
        a.dailyTransactedToday = dailyTransactedToday;
        a.dailyLimitResetAt = dailyLimitResetAt;
        a.currency = currency;
        a.openedAt = openedAt;
        a.closedAt = closedAt;
        a.updatedAt = updatedAt;
        a.version = version;
        return a;
    }

    private Account() { super(); }
}
