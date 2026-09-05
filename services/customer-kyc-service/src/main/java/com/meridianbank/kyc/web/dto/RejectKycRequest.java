package com.meridianbank.kyc.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RejectKycRequest(@NotBlank @Size(max = 500) String reason) {
}
