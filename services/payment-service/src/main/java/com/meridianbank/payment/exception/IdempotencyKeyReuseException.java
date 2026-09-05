package com.meridianbank.payment.exception;

import org.springframework.http.HttpStatus;

public class IdempotencyKeyReuseException extends PaymentException {
    public IdempotencyKeyReuseException() {
        super("IDEMPOTENCY_KEY_REUSE", HttpStatus.CONFLICT,
                "This Idempotency-Key was already used for a different request");
    }
}
