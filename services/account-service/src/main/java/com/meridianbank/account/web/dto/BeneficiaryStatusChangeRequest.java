package com.meridianbank.account.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record BeneficiaryStatusChangeRequest(@NotBlank @Size(max = 500) String reason) {
}
