package com.meridianbank.auth.domain;

/**
 * Login-capability status. Distinct from a customer's banking status (owned by
 * customer-kyc-service) — this only governs whether the identity can authenticate at all.
 * Temporary brute-force lockout is tracked separately via {@link User#getLockedUntil()}.
 */
public enum UserStatus {
    ACTIVE,
    INACTIVE,
    DISABLED
}
