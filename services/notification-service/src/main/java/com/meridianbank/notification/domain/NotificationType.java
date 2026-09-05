package com.meridianbank.notification.domain;

/**
 * See notification-service/README.md for exactly which of these are reachable today.
 * {@code PAYMENT_SUCCESS}, {@code PAYMENT_FAILED}, {@code KYC_STATUS_CHANGED},
 * {@code ACCOUNT_STATUS_CHANGED}, {@code FRAUD_ALERT}, and {@code SUPPORT_REQUEST_RESOLVED} are
 * genuinely reachable — each has a real producer publishing the Kafka event this service consumes
 * to create one. {@code LOGIN_ALERT} and {@code SECURITY_ALERT} are defined (matching this
 * service's original scaffold) but currently unreachable: {@code auth-service} has no Kafka
 * wiring at all (no outbox, Phase 8 never touched it) and publishing login/security events to
 * Kafka is out of this phase's scope — retrofitting auth-service is not "Notification + Customer
 * Support" business logic, so it wasn't done here. Left defined rather than deleted so the type
 * catalog matches the originally-scoped design; a future phase that wires auth-service to Kafka
 * would light these up without any change on this side.
 */
public enum NotificationType {
    PAYMENT_SUCCESS,
    PAYMENT_FAILED,
    LOGIN_ALERT,
    SECURITY_ALERT,
    KYC_STATUS_CHANGED,
    ACCOUNT_STATUS_CHANGED,
    FRAUD_ALERT,
    SUPPORT_REQUEST_RESOLVED
}
