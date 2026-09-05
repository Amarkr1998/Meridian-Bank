package com.meridianbank.fraud.web.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record UpdateFraudRuleRequest(
        @Min(0) int weight,
        @NotNull @DecimalMin(value = "0.00") BigDecimal thresholdNumeric,
        Integer thresholdWindowSeconds,
        Integer thresholdCount,
        boolean enabled
) {
}
