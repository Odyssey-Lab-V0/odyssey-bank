package com.banking.core.infrastructure.persistence.mapper;

import com.banking.core.domain.model.Account;
import com.banking.core.domain.valueobject.AccountNumber;
import com.banking.core.domain.valueobject.Money;
import com.banking.core.infrastructure.persistence.entity.AccountJpaEntity;
import org.springframework.stereotype.Component;

import java.util.Currency;

@Component
public class AccountMapper {

    public AccountJpaEntity toJpa(Account account) {
        return AccountJpaEntity.builder()
                .accountId(account.getId())
                .customerId(account.getCustomerId())
                .accountNumber(account.getAccountNumber().value())
                .accountType(account.getAccountType())
                .status(account.getStatus())
                .currency(account.getCurrency())
                .balance(account.getBalance().amount())
                .dailyTransactionLimit(account.getDailyTransactionLimit().amount())
                .dailyTransactedToday(account.getDailyTransactedToday().amount())
                .dailyLimitResetAt(account.getDailyLimitResetAt())
                .openedAt(account.getOpenedAt())
                .closedAt(account.getClosedAt())
                .updatedAt(account.getUpdatedAt())
                .version(account.getVersion())
                .build();
    }

    public Account toDomain(AccountJpaEntity e) {
        var currency = e.getCurrency();
        return Account.reconstitute(
                e.getAccountId(),
                e.getCustomerId(),
                new AccountNumber(e.getAccountNumber()),
                e.getAccountType(),
                e.getStatus(),
                new Money(e.getBalance(), Currency.getInstance(currency)),
                new Money(e.getDailyTransactionLimit(), Currency.getInstance(currency)),
                new Money(e.getDailyTransactedToday(), Currency.getInstance(currency)),
                e.getDailyLimitResetAt(),
                currency,
                e.getOpenedAt(),
                e.getClosedAt(),
                e.getUpdatedAt(),
                e.getVersion()
        );
    }
}
