package com.meridianbank.fraud.domain;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * One row per {@code POST /api/v1/risk-assessments} call — append-only, never updated. This is
 * both the audit trail of every decision this service has ever made <em>and</em> the data source
 * {@link com.meridianbank.fraud.service.FraudRuleEngine}/{@code AmlSignalEvaluator} query against
 * for velocity/frequency/repeat-behavior rules (there is no other source of transaction history
 * available to this service — see the Trust Boundary note on {@code RiskAssessmentController}).
 */
@Entity
@Table(name = "risk_assessments")
public class RiskAssessment {

    @Id
    private UUID id;

    @Column(name = "transaction_id", nullable = false, unique = true)
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

    /** Comma-separated {@link RuleCode} names that contributed to the score — informational only. */
    @Column(name = "rule_hits", nullable = false, length = 500)
    private String ruleHits;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected RiskAssessment() {
    }

    public RiskAssessment(UUID transactionId, UUID customerId, UUID sourceAccountId, UUID destinationAccountId,
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
        this.createdAt = Instant.now();
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

    public Instant getCreatedAt() {
        return createdAt;
    }
}
