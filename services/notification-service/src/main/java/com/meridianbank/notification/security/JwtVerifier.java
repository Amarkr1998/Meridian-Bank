package com.meridianbank.notification.security;

import com.meridianbank.notification.config.JwtVerificationProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/** Verifies access tokens issued by auth-service — same pattern as every other service. */
@Component
public class JwtVerifier {

    private final SecretKey signingKey;

    public JwtVerifier(JwtVerificationProperties properties) {
        this.signingKey = Keys.hmacShaKeyFor(properties.secret().getBytes(StandardCharsets.UTF_8));
    }

    /** @throws io.jsonwebtoken.JwtException if the token is malformed, expired, or has an invalid signature */
    public DecodedAccessToken verify(String token) {
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
