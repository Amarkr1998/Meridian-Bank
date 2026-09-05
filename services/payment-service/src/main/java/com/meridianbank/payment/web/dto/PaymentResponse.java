package com.meridianbank.payment.web.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.meridianbank.payment.domain.Transaction;
import com.meridianbank.payment.domain.TransactionStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * A FAILED payment is a complete, successfully-handled API result (HTTP 200/201), not an error —
 * see PaymentController. {@code failureCode}/{@code failureReason} are only populated when
 * {@code status == FAILED}.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PaymentResponse(
        UUID id,
        UUID sourceAccountId,
        UUID beneficiaryId,
        UUID destinationAccountId,
        BigDecimal amount,
        String currency,
        String purpose,
        TransactionStatus status,
        String failureCode,
        String failureReason,
        Instant createdAt,
        Instant completedAt
) {
    public static PaymentResponse from(Transaction t) {
        return new PaymentResponse(t.getId(), t.getSourceAccountId(), t.getBeneficiaryId(),
                t.getDestinationAccountId(), t.getAmount(), t.getCurrency(), t.getPurpose(), t.getStatus(),
                t.getFailureCode(), t.getFailureReason(), t.getCreatedAt(), t.getCompletedAt());
    }
}
