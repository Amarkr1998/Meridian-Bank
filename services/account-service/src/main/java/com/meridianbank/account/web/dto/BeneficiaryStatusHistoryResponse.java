package com.meridianbank.account.web.dto;

import com.meridianbank.account.domain.BeneficiaryStatus;
import com.meridianbank.account.domain.BeneficiaryStatusHistory;

import java.time.Instant;
import java.util.UUID;

public record BeneficiaryStatusHistoryResponse(
        UUID id, BeneficiaryStatus oldStatus, BeneficiaryStatus newStatus, String reason,
        UUID changedBy, Instant changedAt
) {
    public static BeneficiaryStatusHistoryResponse from(BeneficiaryStatusHistory h) {
        return new BeneficiaryStatusHistoryResponse(h.getId(), h.getOldStatus(), h.getNewStatus(),
                h.getReason(), h.getChangedBy(), h.getChangedAt());
    }
}
