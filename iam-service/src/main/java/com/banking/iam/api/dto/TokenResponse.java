package com.banking.iam.api.dto;

import java.time.Instant;

public record TokenResponse(
        String accessToken,
        String refreshToken,
        String tokenType,
        Instant refreshExpiresAt
) {
    public static TokenResponse of(String accessToken, String refreshToken, Instant expiresAt) {
        return new TokenResponse(accessToken, refreshToken, "Bearer", expiresAt);
    }
}
