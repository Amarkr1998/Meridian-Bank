package com.meridianbank.auth.web.dto;

import com.meridianbank.auth.domain.UserRole;

import java.util.UUID;

public record TokenResponse(
        String accessToken,
        String refreshToken,
        String tokenType,
        long expiresIn,
        UUID userId,
        String email,
        UserRole role
) {
    public static TokenResponse bearer(String accessToken, String refreshToken, long expiresIn,
                                        UUID userId, String email, UserRole role) {
        return new TokenResponse(accessToken, refreshToken, "Bearer", expiresIn, userId, email, role);
    }
}
