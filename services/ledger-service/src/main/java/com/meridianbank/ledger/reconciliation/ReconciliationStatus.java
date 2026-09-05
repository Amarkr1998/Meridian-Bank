package com.meridianbank.ledger.reconciliation;

/** See docs/reconciliation/reconciliation-design.md. */
public enum ReconciliationStatus {
    MATCHED,
    MISMATCHED,
    PENDING,
    INVESTIGATION,
    RESOLVED
}
