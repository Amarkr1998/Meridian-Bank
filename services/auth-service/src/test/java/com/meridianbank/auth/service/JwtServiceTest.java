package com.meridianbank.auth.service;

import com.meridianbank.auth.config.SecurityProperties;
import com.meridianbank.auth.domain.User;
import com.meridianbank.auth.domain.UserRole;
import io.jsonwebtoken.ExpiredJwtException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JwtServiceTest {

    private SecurityProperties properties(long accessTokenTtlSeconds) {
        return new SecurityProperties(
                new SecurityProperties.Jwt(
                        "unit-test-signing-secret-must-be-at-least-32-bytes-long",
                        accessTokenTtlSeconds, 604_800L, "auth-service-test"),
                new SecurityProperties.Mfa(300, 5, true),
                new SecurityProperties.Lockout(5, 900),
                new SecurityProperties.PasswordReset(1800, true));
    }

    private User sampleUser() {
        return new User("jane.doe@meridianbank.local", "irrelevant-hash", UserRole.CUSTOMER);
    }

    @Test
    void generatesAndParsesAValidToken() {
        JwtService jwtService = new JwtService(properties(900));
        User user = sampleUser();

        String token = jwtService.generateAccessToken(user);
        JwtService.DecodedAccessToken decoded = jwtService.parseAccessToken(token);

        assertThat(decoded.userId()).isEqualTo(user.getId());
        assertThat(decoded.email()).isEqualTo(user.getEmail());
        assertThat(decoded.role()).isEqualTo("CUSTOMER");
    }

    @Test
    void expiredTokenIsRejected() {
        // Negative TTL puts the expiry in the past the instant the token is minted.
        JwtService jwtService = new JwtService(properties(-10));
        String token = jwtService.generateAccessToken(sampleUser());

        assertThrows(ExpiredJwtException.class, () -> jwtService.parseAccessToken(token));
    }
}
