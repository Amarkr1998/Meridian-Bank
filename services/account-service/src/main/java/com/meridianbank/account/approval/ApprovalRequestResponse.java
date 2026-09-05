package com.meridianbank.account.approval;

import java.time.Instant;
import java.util.UUID;

public record ApprovalRequestResponse(
        UUID id, String actionType, UUID resourceId, String reason, String status,
        UUID requestedBy, Instant requestedAt, UUID decidedBy, Instant decidedAt, String decisionNotes
) {
    public static ApprovalRequestResponse from(ApprovalRequest r) {
        return new ApprovalRequestResponse(r.getId(), r.getActionType().name(), r.getResourceId(), r.getReason(),
                r.getStatus().name(), r.getRequestedBy(), r.getRequestedAt(), r.getDecidedBy(), r.getDecidedAt(),
                r.getDecisionNotes());
    }
}
