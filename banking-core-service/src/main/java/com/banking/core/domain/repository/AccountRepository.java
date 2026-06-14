package com.banking.core.domain.repository;

import com.banking.core.domain.model.Account;
import com.banking.core.domain.valueobject.AccountNumber;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AccountRepository {
    Account save(Account account);
    Optional<Account> findById(UUID accountId);
    Optional<Account> findByAccountNumber(AccountNumber accountNumber);
    List<Account> findByCustomerId(UUID customerId);
}
