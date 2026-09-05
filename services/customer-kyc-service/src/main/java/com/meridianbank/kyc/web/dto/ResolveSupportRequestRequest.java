package com.meridianbank.kyc.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ResolveSupportRequestRequest(@NotBlank @Size(max = 1000) String notes) {
}
