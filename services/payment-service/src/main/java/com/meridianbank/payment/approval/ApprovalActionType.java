package com.meridianbank.payment.approval;

/**
 * The one payment-service action gated by maker-checker — see
 * docs/governance/governance-principles.md ("high-value transaction approval"). Releasing a
 * REVIEW-held transaction (see PaymentService#releaseHeldPayment) requires a different staff
 * member to approve than the one who requested the release.
 */
public enum ApprovalActionType {
    RELEASE_PAYMENT
}
