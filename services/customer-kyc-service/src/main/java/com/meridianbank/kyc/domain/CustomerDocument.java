package com.meridianbank.kyc.domain;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

/** Metadata only — never a real identity document. See docs/governance/governance-principles.md. */
@Entity
@Table(name = "customer_documents")
public class CustomerDocument {

    @Id
    private UUID id;

    @Column(name = "kyc_record_id", nullable = false)
    private UUID kycRecordId;

    @Enumerated(EnumType.STRING)
    @Column(name = "document_type", nullable = false, length = 30)
    private DocumentType documentType;

    @Column(name = "document_reference", nullable = false)
    private String documentReference;

    @Column(name = "uploaded_at", nullable = false)
    private Instant uploadedAt;

    protected CustomerDocument() {
    }

    public CustomerDocument(UUID kycRecordId, DocumentType documentType, String documentReference) {
        this.id = UUID.randomUUID();
        this.kycRecordId = kycRecordId;
        this.documentType = documentType;
        this.documentReference = documentReference;
        this.uploadedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getKycRecordId() {
        return kycRecordId;
    }

    public DocumentType getDocumentType() {
        return documentType;
    }

    public String getDocumentReference() {
        return documentReference;
    }

    public Instant getUploadedAt() {
        return uploadedAt;
    }
}
