package com.meridianbank.kyc.web.dto;

import com.meridianbank.kyc.domain.CustomerStatus;
import com.meridianbank.kyc.domain.CustomerStatusHistory;

import java.time.Instant;
import java.util.UUID;

public record CustomerStatusHistoryResponse(
        UUID id, CustomerStatus oldStatus, CustomerStatus newStatus, String reason,
        UUID changedBy, Instant changedAt
) {
    public static CustomerStatusHistoryResponse from(CustomerStatusHistory h) {
        return new CustomerStatusHistoryResponse(h.getId(), h.getOldStatus(), h.getNewStatus(),
                h.getReason(), h.getChangedBy(), h.getChangedAt());
    }
}
