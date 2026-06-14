package com.banking.iam.infrastructure.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Issues and validates JWTs. Stateless — no DB calls here.
 *
 * Access tokens: short-lived (15 min), carry userId + roles.
 * Refresh tokens: long-lived (7 days), opaque UUID stored hashed in DB.
 */
@Service
@Slf4j
public class JwtTokenService {

    private static final String CLAIM_ROLES = "roles";
    private static final String CLAIM_TOKEN_TYPE = "type";
    private static final String TYPE_ACCESS = "access";

    private final SecretKey signingKey;
    private final JwtProperties props;

    public JwtTokenService(JwtProperties props) {
        this.props = props;
        this.signingKey = Keys.hmacShaKeyFor(props.secret().getBytes(StandardCharsets.UTF_8));
    }

    public String issueAccessToken(UUID userId, List<String> roles) {
        var now = Instant.now();
        var expiry = now.plusSeconds(props.accessTokenExpiryMinutes() * 60L);

        return Jwts.builder()
                .subject(userId.toString())
                .claim(CLAIM_ROLES, roles)
                .claim(CLAIM_TOKEN_TYPE, TYPE_ACCESS)
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry))
                .signWith(signingKey)
                .compact();
    }

    /**
     * Validates the token and returns the claims.
     * Returns empty if the token is expired, malformed, or tampered with.
     */
    public Optional<Claims> validate(String token) {
        try {
            var claims = Jwts.parser()
                    .verifyWith(signingKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            return Optional.of(claims);
        } catch (JwtException | IllegalArgumentException ex) {
            log.debug("JWT validation failed: {}", ex.getMessage());
            return Optional.empty();
        }
    }

    public UUID extractUserId(Claims claims) {
        return UUID.fromString(claims.getSubject());
    }

    @SuppressWarnings("unchecked")
    public List<String> extractRoles(Claims claims) {
        return (List<String>) claims.get(CLAIM_ROLES, List.class);
    }
}
