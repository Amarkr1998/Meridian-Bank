package com.meridianbank.payment.web.dto;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.util.UUID;

public record CreatePaymentRequest(
        @NotNull UUID sourceAccountId,
        @NotNull UUID beneficiaryId,
        @NotNull @DecimalMin(value = "0.01") BigDecimal amount,
        @NotBlank @Size(min = 3, max = 3) String currency,
        @Size(max = 200) String purpose
) {
}
