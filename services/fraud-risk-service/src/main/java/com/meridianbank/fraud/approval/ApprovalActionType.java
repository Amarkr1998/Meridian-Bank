package com.meridianbank.fraud.approval;

/**
 * The fixed set of fraud-risk-service actions gated by maker-checker — see
 * docs/governance/governance-principles.md ("high-risk fraud decisions" and "configuration
 * changes"). FRAUD_ALERT_RESOLUTION gates the terminal clear/escalate/confirm decision on a
 * fraud alert (claiming it for review via {@code startReview} remains single-actor — it doesn't
 * decide anything yet). FRAUD_RULE_UPDATE gates changing a rule's weight/thresholds/enabled flag.
 */
public enum ApprovalActionType {
    FRAUD_ALERT_RESOLUTION,
    FRAUD_RULE_UPDATE
}
