package com.meridianbank.kyc.web.dto;

import com.meridianbank.kyc.support.SupportRequest;
import com.meridianbank.kyc.support.SupportRequestCategory;
import com.meridianbank.kyc.support.SupportRequestStatus;

import java.time.Instant;
import java.util.UUID;

public record SupportRequestResponse(
        UUID id, UUID customerId, SupportRequestCategory category, String subject, String description,
        SupportRequestStatus status, UUID assignedTo, Instant assignedAt, UUID resolvedBy, Instant resolvedAt,
        String resolutionNotes, Instant createdAt, Instant updatedAt
) {
    public static SupportRequestResponse from(SupportRequest r) {
        return new SupportRequestResponse(r.getId(), r.getCustomerId(), r.getCategory(), r.getSubject(),
                r.getDescription(), r.getStatus(), r.getAssignedTo(), r.getAssignedAt(), r.getResolvedBy(),
                r.getResolvedAt(), r.getResolutionNotes(), r.getCreatedAt(), r.getUpdatedAt());
    }
}
