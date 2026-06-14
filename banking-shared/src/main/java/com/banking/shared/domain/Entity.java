package com.banking.shared.domain;

import java.util.Objects;

/**
 * Base for domain Entities — equality is identity-based (by ID), not structural.
 *
 * Contrast with ValueObject where equality is value-based.
 * Two Transactions with the same data but different IDs are NOT equal.
 */
public abstract class Entity<ID> {

    private ID id;

    protected Entity(ID id) {
        this.id = Objects.requireNonNull(id, "Entity ID must not be null");
    }

    protected Entity() {
        // for JPA
    }

    public ID getId() {
        return id;
    }

    protected void setId(ID id) {
        this.id = id;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Entity<?> entity = (Entity<?>) o;
        return Objects.equals(id, entity.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
