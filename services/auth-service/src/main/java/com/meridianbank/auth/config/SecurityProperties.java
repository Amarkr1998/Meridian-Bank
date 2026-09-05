package com.meridianbank.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "meridian.security")
public record SecurityProperties(Jwt jwt, Mfa mfa, Lockout lockout, PasswordReset passwordReset) {

    public record Jwt(String secret, long accessTokenTtlSeconds, long refreshTokenTtlSeconds, String issuer) {
    }

    public record Mfa(long otpTtlSeconds, int maxAttempts, boolean demoExposeOtp) {
    }

    public record Lockout(int maxFailedAttempts, long lockoutDurationSeconds) {
    }

    public record PasswordReset(long tokenTtlSeconds, boolean demoExposeToken) {
    }
}
