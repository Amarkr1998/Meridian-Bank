package com.meridianbank.fraud.domain;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Created for every triggered AML signal — see docs/architecture/aml-flow.md. Independent of
 * {@link FraudAlert}/the fraud score: an AML alert never affects the payment's own outcome, it
 * only queues a compliance case (see docs/governance/governance-principles.md's "Simplified AML
 * Disclaimer").
 */
@Entity
@Table(name = "aml_alerts")
public class AmlAlert {

    @Id
    private UUID id;

    @Column(name = "transaction_id", nullable = false)
    private UUID transactionId;

    @Column(name = "customer_id", nullable = false)
    private UUID customerId;

    @Enumerated(EnumType.STRING)
    @Column(name = "signal_code", nullable = false, length = 50)
    private RuleCode signalCode;

    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AmlAlertStatus status;

    @Column(length = 500)
    private String details;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @Column(name = "reviewed_by")
    private UUID reviewedBy;

    @Column(name = "resolution_notes", length = 1000)
    private String resolutionNotes;

    protected AmlAlert() {
    }

    public AmlAlert(UUID transactionId, UUID customerId, RuleCode signalCode, BigDecimal amount, String currency,
                     String details) {
        this.id = UUID.randomUUID();
        this.transactionId = transactionId;
        this.customerId = customerId;
        this.signalCode = signalCode;
        this.amount = amount;
        this.currency = currency;
        this.details = details;
        this.status = AmlAlertStatus.OPEN;
        this.createdAt = Instant.now();
    }

    public void startReview(UUID reviewerId) {
        this.status = AmlAlertStatus.UNDER_REVIEW;
        this.reviewedBy = reviewerId;
    }

    public void resolve(AmlAlertStatus terminalStatus, UUID reviewerId, String notes) {
        this.status = terminalStatus;
        this.reviewedBy = reviewerId;
        this.reviewedAt = Instant.now();
        this.resolutionNotes = notes;
    }

    public UUID getId() {
        return id;
    }

    public UUID getTransactionId() {
        return transactionId;
    }

    public UUID getCustomerId() {
        return customerId;
    }

    public RuleCode getSignalCode() {
        return signalCode;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getCurrency() {
        return currency;
    }

    public AmlAlertStatus getStatus() {
        return status;
    }

    public String getDetails() {
        return details;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getReviewedAt() {
        return reviewedAt;
    }

    public UUID getReviewedBy() {
        return reviewedBy;
    }

    public String getResolutionNotes() {
        return resolutionNotes;
    }
}
