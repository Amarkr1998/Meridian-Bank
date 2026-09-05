package com.meridianbank.account.web.dto;

import com.meridianbank.account.domain.AccountStatus;
import com.meridianbank.account.domain.AccountStatusHistory;

import java.time.Instant;
import java.util.UUID;

public record AccountStatusHistoryResponse(
        UUID id, AccountStatus oldStatus, AccountStatus newStatus, String reason,
        UUID changedBy, Instant changedAt
) {
    public static AccountStatusHistoryResponse from(AccountStatusHistory h) {
        return new AccountStatusHistoryResponse(h.getId(), h.getOldStatus(), h.getNewStatus(),
                h.getReason(), h.getChangedBy(), h.getChangedAt());
    }
}
