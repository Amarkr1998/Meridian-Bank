package com.meridianbank.kyc.web.dto;

import com.meridianbank.kyc.domain.CustomerDocument;
import com.meridianbank.kyc.domain.DocumentType;

import java.time.Instant;
import java.util.UUID;

public record DocumentResponse(UUID id, DocumentType documentType, String documentReference, Instant uploadedAt) {
    public static DocumentResponse from(CustomerDocument d) {
        return new DocumentResponse(d.getId(), d.getDocumentType(), d.getDocumentReference(), d.getUploadedAt());
    }
}
