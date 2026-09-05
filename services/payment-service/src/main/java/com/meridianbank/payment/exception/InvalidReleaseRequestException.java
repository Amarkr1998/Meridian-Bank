package com.meridianbank.payment.exception;

import org.springframework.http.HttpStatus;

public class InvalidReleaseRequestException extends PaymentException {
    public InvalidReleaseRequestException(String message) {
        super("INVALID_RELEASE_REQUEST", HttpStatus.CONFLICT, message);
    }
}
