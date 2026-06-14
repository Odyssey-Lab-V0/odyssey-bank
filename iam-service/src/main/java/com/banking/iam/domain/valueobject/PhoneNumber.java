package com.banking.iam.domain.valueobject;

import com.banking.shared.domain.ValueObject;
import com.banking.shared.domain.exception.BusinessRuleViolationException;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * PhoneNumber Value Object — E.164 international format.
 * Example: +12125551234
 */
public record PhoneNumber(String value) implements ValueObject {

    private static final Pattern E164 = Pattern.compile("^\\+[1-9]\\d{7,14}$");

    public PhoneNumber {
        Objects.requireNonNull(value, "Phone number must not be null");
        value = value.trim().replaceAll("\\s+", "");
        if (!E164.matcher(value).matches()) {
            throw new BusinessRuleViolationException("PHONE_INVALID",
                    "Phone number must be in E.164 format (e.g. +12125551234)");
        }
    }

    public String countryCode() {
        // simplified — production would use libphonenumber
        return value.substring(0, value.length() > 12 ? 3 : 2);
    }

    @Override
    public String toString() {
        return value;
    }
}
