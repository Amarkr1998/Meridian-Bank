package com.meridianbank.account.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record AddBeneficiaryRequest(
        @NotBlank @Size(max = 100) String nickname,
        @NotBlank @Size(max = 200) String beneficiaryName,
        @NotBlank @Pattern(regexp = "^[0-9]{10}$", message = "Account number must be 10 digits") String beneficiaryAccountNumber
) {
}
