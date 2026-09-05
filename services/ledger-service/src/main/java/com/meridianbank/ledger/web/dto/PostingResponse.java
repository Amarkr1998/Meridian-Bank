package com.meridianbank.ledger.web.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record PostingResponse(
        UUID transactionId,
        UUID debitEntryId,
        UUID creditEntryId,
        BigDecimal debitAccountAvailableBalance,
        BigDecimal creditAccountAvailableBalance
) {
}
