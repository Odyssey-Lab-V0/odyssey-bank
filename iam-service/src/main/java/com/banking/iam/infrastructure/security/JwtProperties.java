package com.banking.iam.infrastructure.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "jwt")
public record JwtProperties(
        String secret,
        int accessTokenExpiryMinutes,
        int refreshTokenExpiryDays
) {}
