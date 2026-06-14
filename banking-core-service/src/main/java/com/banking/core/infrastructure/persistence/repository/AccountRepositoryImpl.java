package com.banking.core.infrastructure.persistence.repository;

import com.banking.core.domain.model.Account;
import com.banking.core.domain.repository.AccountRepository;
import com.banking.core.domain.valueobject.AccountNumber;
import com.banking.core.infrastructure.persistence.mapper.AccountMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class AccountRepositoryImpl implements AccountRepository {

    private final AccountJpaRepository jpaRepository;
    private final AccountMapper mapper;

    @Override
    public Account save(Account account) {
        return mapper.toDomain(jpaRepository.save(mapper.toJpa(account)));
    }

    @Override
    public Optional<Account> findById(UUID accountId) {
        return jpaRepository.findById(accountId).map(mapper::toDomain);
    }

    @Override
    public Optional<Account> findByAccountNumber(AccountNumber accountNumber) {
        return jpaRepository.findByAccountNumber(accountNumber.value()).map(mapper::toDomain);
    }

    @Override
    public List<Account> findByCustomerId(UUID customerId) {
        return jpaRepository.findByCustomerId(customerId).stream().map(mapper::toDomain).toList();
    }
}
