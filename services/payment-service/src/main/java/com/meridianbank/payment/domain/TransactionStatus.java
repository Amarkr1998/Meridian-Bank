package com.meridianbank.payment.domain;

/**
 * RISK_CHECK is a structural placeholder until fraud-risk-service exists (Phase 9) — every
 * transaction currently passes it with an automatic ALLOW decision, recorded as such in
 * TransactionStatusHistory. PROCESSING does not post to a real ledger yet (Phase 7) — see
 * payment-service/README.md for what that means for this phase's guarantees.
 */
public enum TransactionStatus {
    INITIATED,
    VALIDATING,
    RISK_CHECK,
    PROCESSING,
    SUCCESS,
    FAILED
}
