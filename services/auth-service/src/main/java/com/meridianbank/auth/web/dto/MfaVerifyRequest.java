package com.meridianbank.auth.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record MfaVerifyRequest(
        @NotBlank String mfaChallengeId,
        @NotBlank @Pattern(regexp = "^[0-9]{6}$", message = "Verification code must be 6 digits") String otp
) {
}
