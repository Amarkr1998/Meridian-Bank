package com.meridianbank.auth.web.dto;

import com.meridianbank.auth.domain.User;
import com.meridianbank.auth.domain.UserRole;
import com.meridianbank.auth.domain.UserStatus;

import java.util.UUID;

public record MeResponse(UUID userId, String email, UserRole role, UserStatus status, boolean mfaEnabled) {
    public static MeResponse from(User user) {
        return new MeResponse(user.getId(), user.getEmail(), user.getRole(), user.getStatus(),
                user.isMfaEnabled());
    }
}
