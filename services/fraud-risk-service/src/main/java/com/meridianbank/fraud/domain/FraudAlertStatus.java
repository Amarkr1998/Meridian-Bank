package com.meridianbank.fraud.domain;

/**
 * OPEN -> UNDER_REVIEW -> {CLEARED, ESCALATED, CONFIRMED_FRAUD} — see
 * docs/architecture/fraud-flow.md's "Review / Escalate / Block / Clear" actions. CONFIRMED_FRAUD is
 * the "Block" action's terminal state: the payment itself was already blocked/failed by
 * payment-service at REVIEW/BLOCK decision time (see PaymentService) — this status is the risk
 * analyst's confirmation that the block was correct, not a separate action that blocks the payment.
 */
public enum FraudAlertStatus {
    OPEN,
    UNDER_REVIEW,
    CLEARED,
    ESCALATED,
    CONFIRMED_FRAUD
}
