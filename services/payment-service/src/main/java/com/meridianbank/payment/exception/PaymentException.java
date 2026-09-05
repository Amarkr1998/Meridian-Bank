package com.meridianbank.payment.exception;

import org.springframework.http.HttpStatus;

/** Base type for payment-service exceptions that are genuine HTTP-level errors — a validation
 *  failure recognized during payment processing (bad source account, KYC not verified, limits
 *  exceeded, ...) is deliberately NOT one of these: it becomes a FAILED transaction returned
 *  with 201, not an HTTP error. See PaymentService and payment-service/README.md. */
public abstract class PaymentException extends RuntimeException {

    private final String errorCode;
    private final HttpStatus httpStatus;

    protected PaymentException(String errorCode, HttpStatus httpStatus, String message) {
        super(message);
        this.errorCode = errorCode;
        this.httpStatus = httpStatus;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public HttpStatus getHttpStatus() {
        return httpStatus;
    }
}
