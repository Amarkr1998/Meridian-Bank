package com.meridianbank.auth.domain;

/**
 * RBAC roles across Meridian Bank. See docs/adr/0010-rbac.md.
 */
public enum UserRole {
    CUSTOMER,
    OPERATIONS,
    COMPLIANCE_OFFICER,
    RISK_ANALYST,
    AUDITOR,
    ADMIN
}
