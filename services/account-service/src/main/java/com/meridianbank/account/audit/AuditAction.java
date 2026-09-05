package com.meridianbank.account.audit;

/**
 * The sensitive actions this service records to the platform-wide audit trail — see
 * docs/adr/0012-audit-architecture.md and audit-service/README.md's action catalog. Duplicated
 * per service (not shared) matching this project's no-shared-library convention (ADR-0003).
 */
public enum AuditAction {
    ACCOUNT_CREATED,
    ACCOUNT_FROZEN,
    ACCOUNT_BLOCKED,
    BENEFICIARY_ADDED,
    APPROVAL_CREATED,
    APPROVAL_COMPLETED
}
