package com.meridianbank.account.domain;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "account_opening_requests")
public class AccountOpeningRequest {

    @Id
    private UUID id;

    @Column(name = "customer_id", nullable = false)
    private UUID customerId;

    @Enumerated(EnumType.STRING)
    @Column(name = "account_type", nullable = false, length = 20)
    private AccountType accountType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AccountOpeningStatus status = AccountOpeningStatus.ACCOUNT_REQUESTED;

    @Column(name = "requested_at", nullable = false)
    private Instant requestedAt;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @Column(name = "reviewed_by")
    private UUID reviewedBy;

    @Column(name = "rejection_reason")
    private String rejectionReason;

    /** Set once {@link #approve} creates the resulting {@link Account}. */
    @Column(name = "account_id")
    private UUID accountId;

    protected AccountOpeningRequest() {
    }

    public AccountOpeningRequest(UUID customerId, AccountType accountType) {
        this.id = UUID.randomUUID();
        this.customerId = customerId;
        this.accountType = accountType;
        this.status = AccountOpeningStatus.ACCOUNT_REQUESTED;
        this.requestedAt = Instant.now();
    }

    public void startReview(UUID reviewer) {
        this.status = AccountOpeningStatus.UNDER_REVIEW;
        this.reviewedBy = reviewer;
    }

    public void approve(UUID reviewer, UUID accountId) {
        this.status = AccountOpeningStatus.APPROVED;
        this.reviewedBy = reviewer;
        this.reviewedAt = Instant.now();
        this.accountId = accountId;
    }

    public void reject(UUID reviewer, String reason) {
        this.status = AccountOpeningStatus.REJECTED;
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

    public AccountType getAccountType() {
        return accountType;
    }

    public AccountOpeningStatus getStatus() {
        return status;
    }

    public Instant getRequestedAt() {
        return requestedAt;
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

    public UUID getAccountId() {
        return accountId;
    }
}
