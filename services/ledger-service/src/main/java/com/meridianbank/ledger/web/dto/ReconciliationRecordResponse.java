package com.meridianbank.ledger.web.dto;

import com.meridianbank.ledger.reconciliation.ReconciliationRecord;
import com.meridianbank.ledger.reconciliation.ReconciliationStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record ReconciliationRecordResponse(
        UUID id, UUID transactionId, BigDecimal internalAmount, String internalCurrency,
        UUID externalTransactionId, BigDecimal externalAmount, String externalCurrency,
        ReconciliationStatus status, String mismatchReason, UUID investigatedBy, Instant investigatedAt,
        UUID resolvedBy, Instant resolvedAt, String resolutionNotes, UUID runId, Instant createdAt, Instant updatedAt
) {
    public static ReconciliationRecordResponse from(ReconciliationRecord r) {
        return new ReconciliationRecordResponse(r.getId(), r.getTransactionId(), r.getInternalAmount(),
                r.getInternalCurrency(), r.getExternalTransactionId(), r.getExternalAmount(), r.getExternalCurrency(),
                r.getStatus(), r.getMismatchReason(), r.getInvestigatedBy(), r.getInvestigatedAt(), r.getResolvedBy(),
                r.getResolvedAt(), r.getResolutionNotes(), r.getRunId(), r.getCreatedAt(), r.getUpdatedAt());
    }
}
