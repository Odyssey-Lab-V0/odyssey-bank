package com.banking.iam.api.dto;

import java.util.UUID;

public record RegisterUserResponse(UUID userId, String message) {}
