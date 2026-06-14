package com.banking.iam.api.rest;

import com.banking.iam.api.dto.RegisterUserRequest;
import com.banking.iam.api.dto.RegisterUserResponse;
import com.banking.iam.application.command.RegisterUserCommand;
import com.banking.iam.application.command.UserApplicationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * REST adapter — translates HTTP requests into application commands.
 *
 * Controllers are thin: validate input, map to command, call application service,
 * map result to response. No business logic here.
 *
 * Layering rule enforced by ArchUnit tests:
 *   api.rest → application (allowed)
 *   api.rest → domain      (forbidden — go through application layer)
 *   api.rest → infrastructure (forbidden)
 */
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserApplicationService userService;

    @PostMapping("/register")
    public ResponseEntity<RegisterUserResponse> register(@Valid @RequestBody RegisterUserRequest req) {
        var command = new RegisterUserCommand(req.email(), req.phoneNumber(), req.password());
        UUID userId = userService.registerUser(command);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(new RegisterUserResponse(userId, "Registration successful. Please verify your email."));
    }

    @PostMapping("/{userId}/verify-email")
    public ResponseEntity<Void> verifyEmail(@PathVariable UUID userId,
                                             @RequestParam String token) {
        // token validation logic would be in a dedicated service
        userService.verifyEmail(userId);
        return ResponseEntity.ok().build();
    }
}
