package com.meridianbank.fraud.web.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

public record RiskAssessmentRequest(
        @NotNull UUID transactionId,
        @NotNull UUID customerId,
        @NotNull UUID sourceAccountId,
        UUID destinationAccountId,
        @NotNull @DecimalMin(value = "0.01") BigDecimal amount,
        @NotBlank String currency
) {
}
