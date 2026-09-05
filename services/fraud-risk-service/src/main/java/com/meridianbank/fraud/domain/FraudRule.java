package com.meridianbank.fraud.domain;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * A single configurable detection rule — see {@link RuleCode}. {@code weight} (points added to the
 * fraud score when triggered; ignored for AML rules, which don't score) and
 * {@code thresholdNumeric}/{@code thresholdWindowSeconds}/{@code thresholdCount} (the rule's
 * tunable parameters — meaning varies per rule, see {@code FraudRuleEngine}/
 * {@code AmlSignalEvaluator}; {@code thresholdCount} is only used by {@code STRUCTURING}, where
 * {@code thresholdNumeric} is already spent on the amount ceiling) are business data, editable by
 * staff via {@code PATCH /api/v1/fraud-rules/{id}} without a code deployment — see
 * docs/governance/governance-principles.md ("Configuration Governance").
 */
@Entity
@Table(name = "fraud_rules")
public class FraudRule {

    @Id
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "rule_code", nullable = false, unique = true, length = 50)
    private RuleCode ruleCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, length = 10)
    private RuleCategory category;

    @Column(nullable = false, length = 255)
    private String description;

    @Column(nullable = false)
    private int weight;

    @Column(name = "threshold_numeric", nullable = false, precision = 18, scale = 2)
    private BigDecimal thresholdNumeric;

    @Column(name = "threshold_window_seconds")
    private Integer thresholdWindowSeconds;

    @Column(name = "threshold_count")
    private Integer thresholdCount;

    @Column(nullable = false)
    private boolean enabled;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "updated_by")
    private UUID updatedBy;

    protected FraudRule() {
    }

    public FraudRule(RuleCode ruleCode, String description, int weight, BigDecimal thresholdNumeric,
                      Integer thresholdWindowSeconds, Integer thresholdCount, boolean enabled) {
        this.id = UUID.randomUUID();
        this.ruleCode = ruleCode;
        this.category = ruleCode.category();
        this.description = description;
        this.weight = weight;
        this.thresholdNumeric = thresholdNumeric;
        this.thresholdWindowSeconds = thresholdWindowSeconds;
        this.thresholdCount = thresholdCount;
        this.enabled = enabled;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public void update(int weight, BigDecimal thresholdNumeric, Integer thresholdWindowSeconds,
                        Integer thresholdCount, boolean enabled, UUID updatedBy) {
        this.weight = weight;
        this.thresholdNumeric = thresholdNumeric;
        this.thresholdWindowSeconds = thresholdWindowSeconds;
        this.thresholdCount = thresholdCount;
        this.enabled = enabled;
        this.updatedBy = updatedBy;
        this.updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public RuleCode getRuleCode() {
        return ruleCode;
    }

    public RuleCategory getCategory() {
        return category;
    }

    public String getDescription() {
        return description;
    }

    public int getWeight() {
        return weight;
    }

    public BigDecimal getThresholdNumeric() {
        return thresholdNumeric;
    }

    public Integer getThresholdWindowSeconds() {
        return thresholdWindowSeconds;
    }

    public Integer getThresholdCount() {
        return thresholdCount;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public UUID getUpdatedBy() {
        return updatedBy;
    }
}
