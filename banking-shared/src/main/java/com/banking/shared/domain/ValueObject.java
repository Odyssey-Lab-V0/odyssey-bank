package com.banking.shared.domain;

/**
 * Marker interface for Value Objects.
 *
 * Value Objects:
 *   - Equality by ALL fields (structural equality), not by identity
 *   - Immutable — no setters, all fields final
 *   - Self-validating — constructor throws if invariant is violated
 *   - No identity (no ID field)
 *
 * Implementations should use Java records or override equals/hashCode on all fields.
 *
 * Examples: Money, Email, PhoneNumber, AccountNumber, Address
 */
public interface ValueObject {
}
