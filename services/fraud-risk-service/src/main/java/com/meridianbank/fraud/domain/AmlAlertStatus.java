package com.meridianbank.fraud.domain;

/** OPEN -> UNDER_REVIEW -> {CLEARED, ESCALATED} — see docs/architecture/aml-flow.md. */
public enum AmlAlertStatus {
    OPEN,
    UNDER_REVIEW,
    CLEARED,
    ESCALATED
}
