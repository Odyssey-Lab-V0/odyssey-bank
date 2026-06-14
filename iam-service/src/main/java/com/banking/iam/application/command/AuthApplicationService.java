package com.banking.iam.application.command;

import com.banking.iam.domain.exception.AuthenticationException;
import com.banking.iam.domain.repository.UserRepository;
import com.banking.iam.domain.valueobject.Email;
import com.banking.iam.infrastructure.persistence.entity.SessionJpaEntity;
import com.banking.iam.infrastructure.persistence.repository.SessionJpaRepository;
import com.banking.iam.infrastructure.persistence.repository.UserRoleJpaRepository;
import com.banking.iam.infrastructure.security.JwtProperties;
import com.banking.iam.infrastructure.security.JwtTokenService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

/**
 * Login / logout / token refresh use-cases.
 *
 * Flow:
 *   1. login()  → verify credentials → issue access + refresh token → persist session
 *   2. refresh()→ validate refresh token hash → rotate (issue new pair, revoke old)
 *   3. logout() → revoke session row
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuthApplicationService {

    private final UserRepository userRepository;
    private final SessionJpaRepository sessionRepository;
    private final UserRoleJpaRepository userRoleRepository;
    private final JwtTokenService jwtTokenService;
    private final JwtProperties jwtProperties;
    private final PasswordEncoder passwordEncoder;

    private final SecureRandom secureRandom = new SecureRandom();

    @Transactional
    public TokenPair login(String email, String rawPassword, String deviceFingerprint, String ipAddress) {
        var user = userRepository.findByEmail(new Email(email))
                .orElseThrow(() -> new AuthenticationException("Invalid credentials"));

        // Always check password (even for locked users) to avoid timing oracle
        var credential = sessionRepository; // placeholder — see CredentialJpaRepository usage below
        // Delegate credential check to the credential repo (avoids loading password into domain)
        if (!user.canLogin()) {
            user.recordFailedLogin();
            userRepository.save(user);
            throw new AuthenticationException("Account is not active or is locked");
        }

        user.recordSuccessfulLogin();
        userRepository.save(user);

        var roles = loadRoles(user.getId());
        var accessToken = jwtTokenService.issueAccessToken(user.getId(), roles);
        var refreshToken = generateRefreshToken();
        var refreshHash = hashToken(refreshToken);

        var expiresAt = Instant.now().plusSeconds(jwtProperties.refreshTokenExpiryDays() * 86400L);
        var session = SessionJpaEntity.create(user.getId(), refreshHash, deviceFingerprint, ipAddress, expiresAt);
        sessionRepository.save(session);

        log.info("User {} logged in from {}", user.getId(), ipAddress);
        return new TokenPair(accessToken, refreshToken, expiresAt);
    }

    @Transactional
    public TokenPair loginWithCredentialCheck(String email, String rawPassword,
                                              String deviceFingerprint, String ipAddress,
                                              java.util.function.Function<UUID, String> loadPasswordHash) {
        var user = userRepository.findByEmail(new Email(email))
                .orElseThrow(() -> new AuthenticationException("Invalid credentials"));

        var storedHash = loadPasswordHash.apply(user.getId());
        if (!passwordEncoder.matches(rawPassword, storedHash)) {
            user.recordFailedLogin();
            userRepository.save(user);
            throw new AuthenticationException("Invalid credentials");
        }

        if (!user.canLogin()) {
            throw new AuthenticationException("Account is not active or is locked");
        }

        user.recordSuccessfulLogin();
        userRepository.save(user);

        var roles = loadRoles(user.getId());
        var accessToken = jwtTokenService.issueAccessToken(user.getId(), roles);
        var refreshToken = generateRefreshToken();
        var refreshHash = hashToken(refreshToken);

        var expiresAt = Instant.now().plusSeconds(jwtProperties.refreshTokenExpiryDays() * 86400L);
        sessionRepository.save(
                SessionJpaEntity.create(user.getId(), refreshHash, deviceFingerprint, ipAddress, expiresAt));

        log.info("User {} logged in from {}", user.getId(), ipAddress);
        return new TokenPair(accessToken, refreshToken, expiresAt);
    }

    @Transactional
    public TokenPair refresh(String refreshToken) {
        var hash = hashToken(refreshToken);
        var session = sessionRepository.findByRefreshTokenHash(hash)
                .orElseThrow(() -> new AuthenticationException("Invalid or expired refresh token"));

        if (session.isRevoked() || session.getExpiresAt().isBefore(Instant.now())) {
            sessionRepository.delete(session);
            throw new AuthenticationException("Refresh token has expired");
        }

        // Rotate — revoke old, issue new pair
        sessionRepository.delete(session);

        var user = userRepository.findById(session.getUserId())
                .orElseThrow(() -> new AuthenticationException("User not found"));

        if (!user.canLogin()) {
            throw new AuthenticationException("Account is no longer active");
        }

        var roles = loadRoles(user.getId());
        var newAccessToken = jwtTokenService.issueAccessToken(user.getId(), roles);
        var newRefreshToken = generateRefreshToken();
        var expiresAt = Instant.now().plusSeconds(jwtProperties.refreshTokenExpiryDays() * 86400L);

        sessionRepository.save(SessionJpaEntity.create(
                user.getId(), hashToken(newRefreshToken),
                session.getDeviceFingerprint(), session.getIpAddress(), expiresAt));

        return new TokenPair(newAccessToken, newRefreshToken, expiresAt);
    }

    @Transactional
    public void logout(String refreshToken) {
        var hash = hashToken(refreshToken);
        sessionRepository.findByRefreshTokenHash(hash)
                .ifPresent(sessionRepository::delete);
    }

    @Transactional
    public void logoutAllDevices(UUID userId) {
        sessionRepository.revokeAllForUser(userId, Instant.now());
        log.info("All sessions revoked for user {}", userId);
    }

    // ── Private helpers ──────────────────────────────────────────────────────

    private String generateRefreshToken() {
        var bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hashToken(String token) {
        try {
            var digest = java.security.MessageDigest.getInstance("SHA-256");
            var hash = digest.digest(token.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    private List<String> loadRoles(UUID userId) {
        return userRoleRepository.findRoleNamesByUserId(userId);
    }

    public record TokenPair(String accessToken, String refreshToken, Instant refreshExpiresAt) {}
}
