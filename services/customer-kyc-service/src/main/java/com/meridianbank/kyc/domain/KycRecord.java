package com.meridianbank.kyc.domain;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

/**
 * Append-only per submission: a rejected customer resubmitting creates a new {@code KycRecord}
 * rather than mutating this one, so the full review history is just every record for a customer,
 * ordered by {@code submittedAt}. See docs/architecture/kyc-flow.md.
 */
@Entity
@Table(name = "kyc_records")
public class KycRecord {

    @Id
    private UUID id;

    @Column(name = "customer_id", nullable = false)
    private UUID customerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private KycStatus status = KycStatus.KYC_PENDING;

    @Column(nullable = false)
    private String nationality;

    @Column(nullable = false)
    private String occupation;

    @Column(name = "submitted_at", nullable = false)
    private Instant submittedAt;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @Column(name = "reviewed_by")
    private UUID reviewedBy;

    @Column(name = "rejection_reason")
    private String rejectionReason;

    protected KycRecord() {
    }

    public KycRecord(UUID customerId, String nationality, String occupation) {
        this.id = UUID.randomUUID();
        this.customerId = customerId;
        this.nationality = nationality;
        this.occupation = occupation;
        this.status = KycStatus.KYC_PENDING;
        this.submittedAt = Instant.now();
    }

    public void startReview(UUID reviewer) {
        this.status = KycStatus.KYC_IN_REVIEW;
        this.reviewedBy = reviewer;
    }

    public void approve(UUID reviewer) {
        this.status = KycStatus.KYC_VERIFIED;
        this.reviewedBy = reviewer;
        this.reviewedAt = Instant.now();
    }

    public void reject(UUID reviewer, String reason) {
        this.status = KycStatus.KYC_REJECTED;
        this.reviewedBy = reviewer;
        this.reviewedAt = Instant.now();
        this.rejectionReason = reason;
    }

    public UUID getId() {
        return id;
    }

    public UUID getCustomerId() {
        return customerId;
    }

    public KycStatus getStatus() {
        return status;
    }

    public String getNationality() {
        return nationality;
    }

    public String getOccupation() {
        return occupation;
    }

    public Instant getSubmittedAt() {
        return submittedAt;
    }

    public Instant getReviewedAt() {
        return reviewedAt;
    }

    public UUID getReviewedBy() {
        return reviewedBy;
    }

    public String getRejectionReason() {
        return rejectionReason;
    }
}
