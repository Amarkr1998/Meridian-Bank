package com.meridianbank.gateway.security;

import com.meridianbank.gateway.config.JwtVerificationProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;

/**
 * Edge check only: does this look like a token auth-service actually issued, and has it not
 * expired? No role/claims are extracted or trusted here for anything proxied downstream — every
 * downstream service independently re-verifies the same token and performs its own
 * resource-level authorization (see ADR-0009's "Alternatives Considered": a gateway-only
 * authorization model was explicitly rejected).
 *
 * <p>The one exception is {@link #decodeRole}, used solely by this gateway's own local
 * {@code SystemHealthController} (Phase 15) — an endpoint with no downstream service to defer
 * role-checking to, so the gateway is genuinely the only place that check can happen.
 */
@Component
public class JwtVerifier {

    private final SecretKey signingKey;

    public JwtVerifier(JwtVerificationProperties properties) {
        this.signingKey = Keys.hmacShaKeyFor(properties.secret().getBytes(StandardCharsets.UTF_8));
    }

    /** @throws io.jsonwebtoken.JwtException if the token is malformed, expired, or has an invalid signature */
    public void verify(String token) {
        Jwts.parser().verifyWith(signingKey).build().parseSignedClaims(token);
    }

    /** @throws io.jsonwebtoken.JwtException if the token is malformed, expired, or has an invalid signature */
    public String decodeRole(String token) {
        Claims claims = Jwts.parser().verifyWith(signingKey).build().parseSignedClaims(token).getPayload();
        return claims.get("role", String.class);
    }
}
