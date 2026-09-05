package com.meridianbank.auth.web.dto;

import com.meridianbank.auth.domain.User;
import com.meridianbank.auth.domain.UserRole;
import com.meridianbank.auth.domain.UserStatus;

import java.time.Instant;
import java.util.UUID;

/** Never includes passwordHash or any token material. */
public record UserResponse(
        UUID id,
        String email,
        UserRole role,
        UserStatus status,
        boolean mfaEnabled,
        Instant lastLoginAt,
        Instant createdAt
) {
    public static UserResponse from(User user) {
        return new UserResponse(
                user.getId(),
                user.getEmail(),
                user.getRole(),
                user.getStatus(),
                user.isMfaEnabled(),
                user.getLastLoginAt(),
                user.getCreatedAt()
        );
    }
}
