package com.meridianbank.fraud.outbox;

public enum OutboxEventStatus {
    PENDING,
    SENT,
    FAILED
}
