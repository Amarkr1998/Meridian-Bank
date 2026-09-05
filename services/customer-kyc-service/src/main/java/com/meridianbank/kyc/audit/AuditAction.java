package com.meridianbank.kyc.audit;

/**
 * The sensitive actions this service records to the platform-wide audit trail — see
 * docs/adr/0012-audit-architecture.md and audit-service/README.md's action catalog. Duplicated
 * per service (not shared) matching this project's no-shared-library convention (ADR-0003).
 */
public enum AuditAction {
    CUSTOMER_REGISTERED,
    KYC_SUBMITTED,
    KYC_APPROVED,
    KYC_REJECTED,
    APPROVAL_CREATED,
    APPROVAL_COMPLETED,
    SUPPORT_REQUEST_CREATED,
    SUPPORT_REQUEST_RESOLVED
}
