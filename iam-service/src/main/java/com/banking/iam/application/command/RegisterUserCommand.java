package com.banking.iam.application.command;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Command object for user registration.
 *
 * Commands represent intent ("please do this"). They carry validated input
 * from the API layer into the application service. They are not domain objects.
 *
 * Validation annotations here are Bean Validation — enforced before the
 * command reaches the application service.
 */
public record RegisterUserCommand(

        @NotBlank @Email
        String email,

        @NotBlank @Pattern(regexp = "^\\+[1-9]\\d{7,14}$", message = "Phone must be E.164 format")
        String phoneNumber,

        @NotBlank @Size(min = 12, max = 128,
                message = "Password must be between 12 and 128 characters")
        String password
) {}
