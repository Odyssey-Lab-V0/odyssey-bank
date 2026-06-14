package com.banking.iam.domain.valueobject;

import com.banking.shared.domain.ValueObject;
import com.banking.shared.domain.exception.BusinessRuleViolationException;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Email Value Object.
 *
 * Immutable. Self-validating. Equality by value.
 * Always stores in lowercase — two emails that differ only in case are the same address.
 */
public record Email(String value) implements ValueObject {

    private static final Pattern RFC5322 = Pattern.compile(
            "^[a-zA-Z0-9_+&*-]+(?:\\.[a-zA-Z0-9_+&*-]+)*@(?:[a-zA-Z0-9-]+\\.)+[a-zA-Z]{2,7}$"
    );
    private static final int MAX_LENGTH = 254;

    public Email {
        Objects.requireNonNull(value, "Email must not be null");
        value = value.trim().toLowerCase(); // canonical form
        if (value.length() > MAX_LENGTH) {
            throw new BusinessRuleViolationException("EMAIL_TOO_LONG",
                    "Email exceeds maximum length of " + MAX_LENGTH);
        }
        if (!RFC5322.matcher(value).matches()) {
            throw new BusinessRuleViolationException("EMAIL_INVALID",
                    "Email format is invalid: " + value);
        }
    }

    public String domain() {
        return value.substring(value.indexOf('@') + 1);
    }

    @Override
    public String toString() {
        return value;
    }
}
