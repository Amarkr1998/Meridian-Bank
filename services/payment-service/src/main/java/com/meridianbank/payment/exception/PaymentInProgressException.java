package com.meridianbank.payment.exception;

import org.springframework.http.HttpStatus;

public class PaymentInProgressException extends PaymentException {
    public PaymentInProgressException() {
        super("PAYMENT_IN_PROGRESS", HttpStatus.CONFLICT,
                "A request with this Idempotency-Key is already being processed — please try again shortly");
    }
}
