package com.banking.core.domain.valueobject;

import com.banking.shared.domain.exception.BusinessRuleViolationException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Currency;

/**
 * Immutable monetary value. Always stores 2 decimal places.
 * All arithmetic returns a new Money — never mutates.
 */
public record Money(BigDecimal amount, Currency currency) {

    public Money {
        if (amount == null) throw new BusinessRuleViolationException("INVALID_AMOUNT", "Amount cannot be null");
        if (currency == null) throw new BusinessRuleViolationException("INVALID_CURRENCY", "Currency cannot be null");
        amount = amount.setScale(2, RoundingMode.HALF_UP);
    }

    public static Money of(BigDecimal amount, String currencyCode) {
        return new Money(amount, Currency.getInstance(currencyCode));
    }

    public static Money of(String amount, String currencyCode) {
        return new Money(new BigDecimal(amount), Currency.getInstance(currencyCode));
    }

    public static Money zero(String currencyCode) {
        return new Money(BigDecimal.ZERO, Currency.getInstance(currencyCode));
    }

    public Money add(Money other) {
        assertSameCurrency(other);
        return new Money(this.amount.add(other.amount), this.currency);
    }

    public Money subtract(Money other) {
        assertSameCurrency(other);
        return new Money(this.amount.subtract(other.amount), this.currency);
    }

    public boolean isPositive() { return amount.compareTo(BigDecimal.ZERO) > 0; }
    public boolean isNegative() { return amount.compareTo(BigDecimal.ZERO) < 0; }
    public boolean isZero()     { return amount.compareTo(BigDecimal.ZERO) == 0; }

    public boolean isGreaterThan(Money other) {
        assertSameCurrency(other);
        return this.amount.compareTo(other.amount) > 0;
    }

    private void assertSameCurrency(Money other) {
        if (!this.currency.equals(other.currency)) {
            throw new BusinessRuleViolationException("CURRENCY_MISMATCH",
                    "Cannot operate on different currencies: " + this.currency + " vs " + other.currency);
        }
    }

    @Override
    public String toString() {
        return currency.getSymbol() + amount.toPlainString();
    }
}
