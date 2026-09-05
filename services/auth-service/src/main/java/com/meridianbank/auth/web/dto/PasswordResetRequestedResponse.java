package com.meridianbank.auth.web.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Always returned with the same {@code message} regardless of whether the email matched an
 * account, to avoid confirming account existence. {@code devResetToken} is populated only when
 * running in demo mode AND a matching account exists — see auth-service/README.md for why this
 * is an accepted, explicitly documented trade-off in the absence of a real email channel
 * (notification-service, Phase 13), and why it must never be enabled outside local/demo use.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PasswordResetRequestedResponse(String message, String devResetToken) {

    private static final String MESSAGE =
            "If an account exists for this email, password reset instructions have been issued.";

    public static PasswordResetRequestedResponse withoutToken() {
        return new PasswordResetRequestedResponse(MESSAGE, null);
    }

    public static PasswordResetRequestedResponse withDevToken(String token) {
        return new PasswordResetRequestedResponse(MESSAGE, token);
    }
}
