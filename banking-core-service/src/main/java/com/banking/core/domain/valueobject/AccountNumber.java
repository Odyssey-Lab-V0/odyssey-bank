package com.banking.core.domain.valueobject;

import com.banking.shared.domain.exception.BusinessRuleViolationException;

import java.util.concurrent.ThreadLocalRandom;

public record AccountNumber(String value) {

    public AccountNumber {
        if (value == null || !value.matches("\\d{16}")) {
            throw new BusinessRuleViolationException("INVALID_ACCOUNT_NUMBER", "Account number must be 16 digits");
        }
    }

    public static AccountNumber generate() {
        long number = ThreadLocalRandom.current().nextLong(1_000_000_000_000_000L, 9_999_999_999_999_999L);
        return new AccountNumber(String.valueOf(number));
    }
}
