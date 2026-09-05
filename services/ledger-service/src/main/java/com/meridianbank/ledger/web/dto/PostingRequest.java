package com.meridianbank.ledger.web.dto;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.util.UUID;

public record PostingRequest(
        @NotNull UUID transactionId,
        @NotNull UUID debitAccountId,
        @NotNull UUID creditAccountId,
        @NotNull @DecimalMin(value = "0.01") BigDecimal amount,
        @NotBlank @Size(min = 3, max = 3) String currency,
        @Size(max = 200) String reference
) {
}
