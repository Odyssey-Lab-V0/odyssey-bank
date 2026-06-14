package com.banking.iam.api.rest;

import com.banking.iam.api.dto.LoginRequest;
import com.banking.iam.api.dto.TokenResponse;
import com.banking.iam.application.command.AuthApplicationService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthApplicationService authService;

    @PostMapping("/login")
    public ResponseEntity<TokenResponse> login(@Valid @RequestBody LoginRequest request,
                                               HttpServletRequest httpRequest) {
        var ip = extractClientIp(httpRequest);
        var fingerprint = httpRequest.getHeader("X-Device-Fingerprint");

        var tokenPair = authService.login(request.email(), request.password(), fingerprint, ip);

        return ResponseEntity.ok(TokenResponse.of(
                tokenPair.accessToken(),
                tokenPair.refreshToken(),
                tokenPair.refreshExpiresAt()
        ));
    }

    /**
     * POST /api/v1/auth/refresh
     * Rotates the refresh token — old one is invalidated immediately.
     */
    @PostMapping("/refresh")
    public ResponseEntity<TokenResponse> refresh(@RequestBody Map<String, String> body) {
        var refreshToken = body.get("refreshToken");
        if (refreshToken == null || refreshToken.isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        var tokenPair = authService.refresh(refreshToken);
        return ResponseEntity.ok(TokenResponse.of(
                tokenPair.accessToken(),
                tokenPair.refreshToken(),
                tokenPair.refreshExpiresAt()
        ));
    }

    /**
     * POST /api/v1/auth/logout
     * Revokes the session for this device.
     */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@RequestBody Map<String, String> body) {
        var refreshToken = body.get("refreshToken");
        if (refreshToken != null) {
            authService.logout(refreshToken);
        }
        return ResponseEntity.noContent().build();
    }

    private String extractClientIp(HttpServletRequest request) {
        var forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
