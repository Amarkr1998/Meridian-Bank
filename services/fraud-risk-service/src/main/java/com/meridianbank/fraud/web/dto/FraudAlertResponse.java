package com.meridianbank.fraud.web.dto;

import com.meridianbank.fraud.domain.FraudAlert;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record FraudAlertResponse(
        UUID id, UUID transactionId, UUID customerId, UUID sourceAccountId, UUID destinationAccountId,
        BigDecimal amount, String currency, int score, String decision, String ruleHits, String status,
        Instant createdAt, Instant reviewedAt, UUID reviewedBy, String resolutionNotes
) {
    public static FraudAlertResponse from(FraudAlert a) {
        return new FraudAlertResponse(a.getId(), a.getTransactionId(), a.getCustomerId(), a.getSourceAccountId(),
                a.getDestinationAccountId(), a.getAmount(), a.getCurrency(), a.getScore(), a.getDecision().name(),
                a.getRuleHits(), a.getStatus().name(), a.getCreatedAt(), a.getReviewedAt(), a.getReviewedBy(),
                a.getResolutionNotes());
    }
}
