package com.meridianbank.fraud.service;

import java.math.BigDecimal;
import java.util.UUID;

/** The transaction context a risk assessment is evaluated against — see RiskAssessmentController. */
public record RiskContext(UUID transactionId, UUID customerId, UUID sourceAccountId, UUID destinationAccountId,
                           BigDecimal amount, String currency) {
}
