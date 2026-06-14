package com.banking.core.application.command;

import com.banking.core.domain.model.Account;
import com.banking.core.domain.model.AccountType;
import com.banking.core.domain.repository.AccountRepository;
import com.banking.core.domain.valueobject.AccountNumber;
import com.banking.core.domain.valueobject.Money;
import com.banking.core.infrastructure.persistence.entity.CoreOutboxEvent;
import com.banking.core.infrastructure.persistence.entity.LedgerEntryJpaEntity;
import com.banking.core.infrastructure.persistence.repository.CoreOutboxRepository;
import com.banking.core.infrastructure.persistence.repository.LedgerEntryJpaRepository;
import com.banking.shared.domain.exception.BusinessRuleViolationException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AccountApplicationService {

    private final AccountRepository accountRepository;
    private final LedgerEntryJpaRepository ledgerRepository;
    private final CoreOutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public Account openAccount(UUID customerId, String accountType, String currency, BigDecimal initialDeposit) {
        var type = AccountType.valueOf(accountType.toUpperCase());
        var deposit = initialDeposit != null
                ? Money.of(initialDeposit, currency)
                : Money.zero(currency);

        var account = Account.open(customerId, type, currency, deposit);
        account = accountRepository.save(account);

        // Write initial deposit as ledger entry
        if (deposit.isPositive()) {
            ledgerRepository.save(LedgerEntryJpaEntity.builder()
                    .entryId(UUID.randomUUID())
                    .transactionId(UUID.randomUUID())
                    .accountId(account.getId())
                    .entryType("CREDIT")
                    .amount(deposit.amount())
                    .currency(currency)
                    .description("Initial deposit")
                    .postedAt(Instant.now())
                    .build());
        }

        drainEventsToOutbox(account);
        log.info("Account {} opened for customer {}", account.getAccountNumber().value(), customerId);
        return account;
    }

    @Transactional
    public void debit(UUID accountId, BigDecimal amount, String currency, String reference, UUID idempotencyKey) {
        var account = accountRepository.findById(accountId)
                .orElseThrow(() -> new BusinessRuleViolationException("ACCOUNT_NOT_FOUND", "Account not found: " + accountId));

        var money = Money.of(amount, currency);
        account.debit(money, reference);
        accountRepository.save(account);

        ledgerRepository.save(LedgerEntryJpaEntity.builder()
                .entryId(idempotencyKey)
                .transactionId(idempotencyKey)
                .accountId(accountId)
                .entryType("DEBIT")
                .amount(amount)
                .currency(currency)
                .description(reference)
                .postedAt(Instant.now())
                .build());

        drainEventsToOutbox(account);
    }

    @Transactional
    public void credit(UUID accountId, BigDecimal amount, String currency, String reference, UUID idempotencyKey) {
        var account = accountRepository.findById(accountId)
                .orElseThrow(() -> new BusinessRuleViolationException("ACCOUNT_NOT_FOUND", "Account not found: " + accountId));

        var money = Money.of(amount, currency);
        account.credit(money, reference);
        accountRepository.save(account);

        ledgerRepository.save(LedgerEntryJpaEntity.builder()
                .entryId(idempotencyKey)
                .transactionId(idempotencyKey)
                .accountId(accountId)
                .entryType("CREDIT")
                .amount(amount)
                .currency(currency)
                .description(reference)
                .postedAt(Instant.now())
                .build());

        drainEventsToOutbox(account);
    }

    @Transactional
    public void transfer(UUID fromAccountId, UUID toAccountId, BigDecimal amount, String currency, String reference) {
        var txnId = UUID.randomUUID();
        var from = accountRepository.findById(fromAccountId)
                .orElseThrow(() -> new BusinessRuleViolationException("ACCOUNT_NOT_FOUND", "Source account not found"));
        var to = accountRepository.findById(toAccountId)
                .orElseThrow(() -> new BusinessRuleViolationException("ACCOUNT_NOT_FOUND", "Destination account not found"));

        var money = Money.of(amount, currency);
        from.debit(money, reference);
        to.credit(money, reference);

        accountRepository.save(from);
        accountRepository.save(to);

        var now = Instant.now();
        ledgerRepository.saveAll(List.of(
                LedgerEntryJpaEntity.builder()
                        .entryId(UUID.randomUUID()).transactionId(txnId)
                        .accountId(fromAccountId).entryType("DEBIT")
                        .amount(amount).currency(currency).description(reference).postedAt(now).build(),
                LedgerEntryJpaEntity.builder()
                        .entryId(UUID.randomUUID()).transactionId(txnId)
                        .accountId(toAccountId).entryType("CREDIT")
                        .amount(amount).currency(currency).description(reference).postedAt(now).build()
        ));

        drainEventsToOutbox(from);
        drainEventsToOutbox(to);
    }

    public List<Account> getAccountsByCustomer(UUID customerId) {
        return accountRepository.findByCustomerId(customerId);
    }

    public Account getAccount(UUID accountId) {
        return accountRepository.findById(accountId)
                .orElseThrow(() -> new BusinessRuleViolationException("ACCOUNT_NOT_FOUND", "Account not found"));
    }

    // ── Outbox ───────────────────────────────────────────────────────────────

    @SneakyThrows
    private void drainEventsToOutbox(Account account) {
        for (var event : account.pullDomainEvents()) {
            var payload = objectMapper.writeValueAsString(event);
            outboxRepository.save(new CoreOutboxEvent(
                    UUID.randomUUID(), "Account", account.getId(),
                    event.getClass().getSimpleName(), payload));
        }
    }
}
