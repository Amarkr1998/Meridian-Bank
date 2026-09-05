package com.meridianbank.account.web.dto;

import com.meridianbank.account.domain.AccountOpeningRequest;
import com.meridianbank.account.domain.AccountOpeningStatus;
import com.meridianbank.account.domain.AccountType;

import java.time.Instant;
import java.util.UUID;

public record AccountRequestResponse(
        UUID id, UUID customerId, AccountType accountType, AccountOpeningStatus status,
        Instant requestedAt, Instant reviewedAt, UUID reviewedBy, String rejectionReason, UUID accountId
) {
    public static AccountRequestResponse from(AccountOpeningRequest r) {
        return new AccountRequestResponse(r.getId(), r.getCustomerId(), r.getAccountType(), r.getStatus(),
                r.getRequestedAt(), r.getReviewedAt(), r.getReviewedBy(), r.getRejectionReason(), r.getAccountId());
    }
}
