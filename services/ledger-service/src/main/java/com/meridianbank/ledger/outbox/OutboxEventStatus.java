package com.meridianbank.ledger.outbox;

public enum OutboxEventStatus {
    PENDING,
    SENT,
    FAILED
}
