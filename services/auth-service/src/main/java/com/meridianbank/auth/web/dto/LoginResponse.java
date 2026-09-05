package com.meridianbank.auth.web.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Either an MFA challenge (credentials valid, second factor required) or issued tokens
 * (MFA disabled for this identity). Exactly one of {@code tokens} / the challenge fields is set.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record LoginResponse(
        boolean mfaRequired,
        String mfaChallengeId,
        Long expiresInSeconds,
        String devOtp,
        TokenResponse tokens
) {
    public static LoginResponse mfaChallenge(String mfaChallengeId, long expiresInSeconds, String devOtp) {
        return new LoginResponse(true, mfaChallengeId, expiresInSeconds, devOtp, null);
    }

    public static LoginResponse tokens(TokenResponse tokens) {
        return new LoginResponse(false, null, null, null, tokens);
    }
}
