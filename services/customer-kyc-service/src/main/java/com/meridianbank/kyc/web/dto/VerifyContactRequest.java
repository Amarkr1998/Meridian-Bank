package com.meridianbank.kyc.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record VerifyContactRequest(
        @NotBlank @Pattern(regexp = "^[0-9]{6}$", message = "Verification code must be 6 digits") String otp
) {
}
