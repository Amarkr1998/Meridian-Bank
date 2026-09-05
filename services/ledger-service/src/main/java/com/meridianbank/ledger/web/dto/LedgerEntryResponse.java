package com.meridianbank.ledger.web.dto;

import com.meridianbank.ledger.domain.EntryType;
import com.meridianbank.ledger.domain.LedgerEntry;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record LedgerEntryResponse(
        UUID ledgerEntryId, UUID transactionId, UUID accountId, EntryType entryType,
        BigDecimal amount, String currency, String reference, Instant createdAt
) {
    public static LedgerEntryResponse from(LedgerEntry e) {
        return new LedgerEntryResponse(e.getLedgerEntryId(), e.getTransactionId(), e.getAccountId(),
                e.getEntryType(), e.getAmount(), e.getCurrency(), e.getReference(), e.getCreatedAt());
    }
}
