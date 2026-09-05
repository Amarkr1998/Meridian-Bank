package com.meridianbank.fraud.web.dto;

import com.meridianbank.fraud.domain.AmlAlert;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record AmlAlertResponse(
        UUID id, UUID transactionId, UUID customerId, String signalCode, BigDecimal amount, String currency,
        String status, String details, Instant createdAt, Instant reviewedAt, UUID reviewedBy,
        String resolutionNotes
) {
    public static AmlAlertResponse from(AmlAlert a) {
        return new AmlAlertResponse(a.getId(), a.getTransactionId(), a.getCustomerId(), a.getSignalCode().name(),
                a.getAmount(), a.getCurrency(), a.getStatus().name(), a.getDetails(), a.getCreatedAt(),
                a.getReviewedAt(), a.getReviewedBy(), a.getResolutionNotes());
    }
}
