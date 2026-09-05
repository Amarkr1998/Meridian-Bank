package com.meridianbank.fraud.domain;

/**
 * The fixed set of detection rules this phase implements — see docs/architecture/fraud-flow.md and
 * aml-flow.md for the rule categories they were derived from. Which rules *exist* is code (adding a
 * new one is a code change to {@code FraudRuleEngine}/{@code AmlSignalEvaluator}); each rule's
 * *weight/threshold/enabled* is configurable data in the {@code fraud_rules} table — see
 * docs/governance/governance-principles.md ("Configuration Governance").
 */
public enum RuleCode {
    /** amount >= threshold. */
    HIGH_AMOUNT(RuleCategory.FRAUD),
    /** count of this customer's risk assessments in the trailing window >= threshold. */
    HIGH_VELOCITY(RuleCategory.FRAUD),
    /** first-ever assessment for this (customer, destination account) pair AND amount >= threshold. */
    NEW_BENEFICIARY_HIGH_AMOUNT(RuleCategory.FRAUD),
    /** count of this customer's REVIEW/BLOCK decisions in the trailing window >= threshold. */
    REPEAT_RISKY_BEHAVIOR(RuleCategory.FRAUD),
    /** count of this customer's assessments in a short trailing window >= threshold. */
    RAPID_SEQUENTIAL_TRANSFERS(RuleCategory.FRAUD),

    /** amount >= threshold (a materially higher bar than HIGH_AMOUNT). */
    HIGH_VALUE(RuleCategory.AML),
    /** sum of this customer's transaction amounts in the trailing window >= threshold. */
    VELOCITY(RuleCategory.AML),
    /** count of this customer's transactions in [threshold * 0.8, threshold) in the trailing
     *  window >= a fixed count — a simplified structuring signal (transfers clustered just under
     *  a reporting threshold). */
    STRUCTURING(RuleCategory.AML),
    /** count of this customer's transactions to the same destination account in the trailing
     *  window >= threshold. */
    REPEATED_NEW_BENEFICIARY(RuleCategory.AML);

    private final RuleCategory category;

    RuleCode(RuleCategory category) {
        this.category = category;
    }

    public RuleCategory category() {
        return category;
    }
}
