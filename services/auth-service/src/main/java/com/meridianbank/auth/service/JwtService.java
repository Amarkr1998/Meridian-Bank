package com.meridianbank.auth.service;

import com.meridianbank.auth.config.SecurityProperties;
import com.meridianbank.auth.domain.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

/** Issues and validates short-lived JWT access tokens. See docs/adr/0006-idempotency.md and
 *  docs/security/security-architecture.md for how this fits the wider auth flow. */
@Service
public class JwtService {

    private final SecurityProperties properties;
    private final SecretKey signingKey;

    public JwtService(SecurityProperties properties) {
        this.properties = properties;
        this.signingKey = Keys.hmacShaKeyFor(properties.jwt().secret().getBytes(StandardCharsets.UTF_8));
    }

    public String generateAccessToken(User user) {
        Instant now = Instant.now();
        Instant expiry = now.plusSeconds(properties.jwt().accessTokenTtlSeconds());
        return Jwts.builder()
                .issuer(properties.jwt().issuer())
                .subject(user.getId().toString())
                .claim("email", user.getEmail())
                .claim("role", user.getRole().name())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry))
                .signWith(signingKey)
                .compact();
    }

    public long getAccessTokenTtlSeconds() {
        return properties.jwt().accessTokenTtlSeconds();
    }

    public long getRefreshTokenTtlSeconds() {
        return properties.jwt().refreshTokenTtlSeconds();
    }

    /** @throws io.jsonwebtoken.JwtException if the token is malformed, expired, or has an invalid signature */
    public DecodedAccessToken parseAccessToken(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
        return new DecodedAccessToken(
                UUID.fromString(claims.getSubject()),
                claims.get("email", String.class),
                claims.get("role", String.class)
        );
    }

    public record DecodedAccessToken(UUID userId, String email, String role) {
    }
}
