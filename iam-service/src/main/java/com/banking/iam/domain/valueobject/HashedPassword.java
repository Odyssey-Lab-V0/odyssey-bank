package com.banking.iam.domain.valueobject;

import com.banking.shared.domain.ValueObject;
import com.banking.shared.domain.exception.BusinessRuleViolationException;

import java.util.Objects;

/**
 * HashedPassword Value Object.
 *
 * CRITICAL RULES:
 *  - Never store plaintext passwords anywhere — not in logs, not in DB, not in memory beyond hashing
 *  - Only BCrypt $2b$ hashes are accepted (prefix check)
 *  - This VO cannot be used to retrieve the original password — compare-only
 *
 * Construction:
 *   HashedPassword.fromPlaintext("secret", passwordEncoder) — hashes and wraps
 *   HashedPassword.fromHash("$2b$12$...")                   — wraps existing hash from DB
 */
public record HashedPassword(String hash) implements ValueObject {

    public HashedPassword {
        Objects.requireNonNull(hash, "Password hash must not be null");
        if (!hash.startsWith("$2b$") && !hash.startsWith("$2a$")) {
            throw new BusinessRuleViolationException("INVALID_HASH_ALGORITHM",
                    "Only BCrypt hashes are accepted");
        }
    }

    public static HashedPassword fromHash(String bcryptHash) {
        return new HashedPassword(bcryptHash);
    }

    /** Use this in the application layer — never expose raw hash creation to the domain */
    public boolean matches(String plaintext, PasswordEncoder encoder) {
        return encoder.matches(plaintext, this.hash);
    }

    @Override
    public String toString() {
        return "[PROTECTED]"; // never log the hash
    }

    /** Functional interface so the domain layer doesn't depend on Spring Security directly */
    @FunctionalInterface
    public interface PasswordEncoder {
        boolean matches(CharSequence rawPassword, String encodedPassword);
    }
}
