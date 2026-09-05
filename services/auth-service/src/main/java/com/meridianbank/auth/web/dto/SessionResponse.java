package com.meridianbank.auth.web.dto;

import com.meridianbank.auth.domain.RefreshToken;

import java.time.Instant;
import java.util.UUID;

public record SessionResponse(
        UUID id,
        Instant issuedAt,
        Instant expiresAt,
        String createdByIp
) {
    public static SessionResponse from(RefreshToken token) {
        return new SessionResponse(token.getId(), token.getIssuedAt(), token.getExpiresAt(),
                token.getCreatedByIp());
    }
}
