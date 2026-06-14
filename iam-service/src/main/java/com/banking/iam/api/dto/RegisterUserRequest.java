package com.banking.iam.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterUserRequest(
        @NotBlank @Email String email,
        @NotBlank @Pattern(regexp = "^\\+[1-9]\\d{7,14}$") String phoneNumber,
        @NotBlank @Size(min = 13, max = 128) String password
) {}
