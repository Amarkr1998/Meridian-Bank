package com.meridianbank.payment.outbox;

public enum OutboxEventStatus {
    PENDING,
    SENT,
    FAILED
}
