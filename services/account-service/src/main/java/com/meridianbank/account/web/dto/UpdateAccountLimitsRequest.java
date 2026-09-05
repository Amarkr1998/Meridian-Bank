package com.meridianbank.account.web.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record UpdateAccountLimitsRequest(
        @NotNull @DecimalMin(value = "0.00") BigDecimal perTransactionLimit,
        @NotNull @DecimalMin(value = "0.00") BigDecimal dailyLimit
) {
}
