package com.meridianbank.kyc.web.dto;

import com.meridianbank.kyc.domain.CustomerStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpdateCustomerStatusRequest(
        @NotNull CustomerStatus status,
        @NotBlank @Size(max = 500) String reason
) {
}
