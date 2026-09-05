package com.meridianbank.kyc.web.dto;

import com.meridianbank.kyc.domain.KycRecord;
import com.meridianbank.kyc.domain.KycStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record KycRecordResponse(
        UUID id, UUID customerId, KycStatus status, String nationality, String occupation,
        Instant submittedAt, Instant reviewedAt, UUID reviewedBy, String rejectionReason,
        List<DocumentResponse> documents
) {
    public static KycRecordResponse from(KycRecord r, List<DocumentResponse> documents) {
        return new KycRecordResponse(r.getId(), r.getCustomerId(), r.getStatus(), r.getNationality(),
                r.getOccupation(), r.getSubmittedAt(), r.getReviewedAt(), r.getReviewedBy(),
                r.getRejectionReason(), documents);
    }
}
