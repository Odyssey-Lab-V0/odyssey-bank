package com.banking.aml.domain.model;

public enum AlertType {
    LARGE_TRANSACTION,      // single tx > $10,000
    RAPID_MOVEMENT,         // many txns in short window
    ROUND_AMOUNT,           // suspiciously round amounts
    STRUCTURING             // multiple txns just below reporting threshold
}
