package com.meridianbank.account.outbox;

public enum OutboxEventStatus {
    PENDING,
    SENT,
    FAILED
}
