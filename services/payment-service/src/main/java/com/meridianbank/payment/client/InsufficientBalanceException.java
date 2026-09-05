package com.meridianbank.payment.client;

/**
 * Deliberately NOT a {@link com.meridianbank.payment.exception.PaymentException}: this is caught
 * inside PaymentService and converted into a FAILED transaction (returned as 201, per this
 * service's error-modeling convention — see payment-service/README.md), never allowed to
 * propagate to GlobalExceptionHandler as an HTTP error.
 */
public class InsufficientBalanceException extends RuntimeException {
    public InsufficientBalanceException() {
        super("Source account has insufficient available balance");
    }
}
