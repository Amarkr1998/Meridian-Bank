package com.meridianbank.kyc.approval;

/** PENDING_APPROVAL -> {APPROVED, REJECTED} — see docs/architecture/maker-checker-flow.md. */
public enum ApprovalStatus {
    PENDING_APPROVAL,
    APPROVED,
    REJECTED
}
