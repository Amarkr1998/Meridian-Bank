package com.meridianbank.kyc.web.dto;

import com.meridianbank.kyc.support.SupportRequestCategory;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateSupportRequestRequest(
        @NotNull SupportRequestCategory category,
        @NotBlank @Size(max = 200) String subject,
        @NotBlank @Size(max = 2000) String description
) {
}
