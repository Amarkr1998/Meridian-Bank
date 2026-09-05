package com.meridianbank.ledger.web.dto;

import com.meridianbank.ledger.domain.Balance;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record BalanceResponse(
        UUID accountId, BigDecimal availableBalance, BigDecimal ledgerBalance, String currency, Instant updatedAt
) {
    public static BalanceResponse from(Balance b) {
        return new BalanceResponse(b.getAccountId(), b.getAvailableBalance(), b.getLedgerBalance(),
                b.getCurrency(), b.getUpdatedAt());
    }
}
