package com.banking.shared.domain.exception;

/**
 * Root exception for all domain rule violations.
 *
 * Throw this (or a subclass) when an aggregate invariant is violated.
 * The API layer catches it and maps to HTTP 422 Unprocessable Entity.
 *
 * Never throw infrastructure exceptions (SQLException, IOException) from
 * the domain layer — only DomainException and its subtypes.
 */
public class DomainException extends RuntimeException {

    private final String errorCode;

    public DomainException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public DomainException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
