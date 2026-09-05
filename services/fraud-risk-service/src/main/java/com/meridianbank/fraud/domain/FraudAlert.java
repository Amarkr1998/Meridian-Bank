package com.meridianbank.fraud.domain;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Created for every REVIEW/BLOCK decision — see docs/architecture/fraud-flow.md. */
@Entity
@Table(name = "fraud_alerts")
public class FraudAlert {

    @Id
    private UUID id;

    @Column(name = "transaction_id", nullable = false)
    private UUID transactionId;

    @Column(name = "customer_id", nullable = false)
    private UUID customerId;

    @Column(name = "source_account_id", nullable = false)
    private UUID sourceAccountId;

    @Column(name = "destination_account_id")
    private UUID destinationAccountId;

    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(nullable = false)
    private int score;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private RiskDecision decision;

    @Column(name = "rule_hits", nullable = false, length = 500)
    private String ruleHits;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private FraudAlertStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @Column(name = "reviewed_by")
    private UUID reviewedBy;

    @Column(name = "resolution_notes", length = 1000)
    private String resolutionNotes;

    protected FraudAlert() {
    }

    public FraudAlert(UUID transactionId, UUID customerId, UUID sourceAccountId, UUID destinationAccountId,
                       BigDecimal amount, String currency, int score, RiskDecision decision, String ruleHits) {
        this.id = UUID.randomUUID();
        this.transactionId = transactionId;
        this.customerId = customerId;
        this.sourceAccountId = sourceAccountId;
        this.destinationAccountId = destinationAccountId;
        this.amount = amount;
        this.currency = currency;
        this.score = score;
        this.decision = decision;
        this.ruleHits = ruleHits;
        this.status = FraudAlertStatus.OPEN;
        this.createdAt = Instant.now();
    }

    public void startReview(UUID reviewerId) {
        this.status = FraudAlertStatus.UNDER_REVIEW;
        this.reviewedBy = reviewerId;
    }

    public void resolve(FraudAlertStatus terminalStatus, UUID reviewerId, String notes) {
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

    public UUID getSourceAccountId() {
        return sourceAccountId;
    }

    public UUID getDestinationAccountId() {
        return destinationAccountId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getCurrency() {
        return currency;
    }

    public int getScore() {
        return score;
    }

    public RiskDecision getDecision() {
        return decision;
    }

    public String getRuleHits() {
        return ruleHits;
    }

    public FraudAlertStatus getStatus() {
        return status;
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
