package com.meridianbank.kyc.exception;

import org.springframework.http.HttpStatus;

public class AuthServiceUnavailableException extends KycException {
    public AuthServiceUnavailableException(Throwable cause) {
        super("AUTH_SERVICE_UNAVAILABLE", HttpStatus.SERVICE_UNAVAILABLE,
                "Could not reach the identity service — please try again shortly");
        initCause(cause);
    }
}
