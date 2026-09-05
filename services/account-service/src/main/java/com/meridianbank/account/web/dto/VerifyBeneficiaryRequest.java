package com.meridianbank.account.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record VerifyBeneficiaryRequest(
        @NotBlank @Pattern(regexp = "^[0-9]{6}$", message = "Verification code must be 6 digits") String otp
) {
}
