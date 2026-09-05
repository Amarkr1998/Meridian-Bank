package com.meridianbank.payment.web.dto;

import com.meridianbank.payment.domain.TransactionStatus;
import com.meridianbank.payment.domain.TransactionStatusHistory;

import java.time.Instant;
import java.util.UUID;

public record TransactionStatusHistoryResponse(
        UUID id, TransactionStatus oldStatus, TransactionStatus newStatus, String reason, Instant changedAt
) {
    public static TransactionStatusHistoryResponse from(TransactionStatusHistory h) {
        return new TransactionStatusHistoryResponse(h.getId(), h.getOldStatus(), h.getNewStatus(),
                h.getReason(), h.getChangedAt());
    }
}
