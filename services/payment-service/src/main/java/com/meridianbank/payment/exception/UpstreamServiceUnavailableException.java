package com.meridianbank.payment.exception;

import org.springframework.http.HttpStatus;

public class UpstreamServiceUnavailableException extends PaymentException {
    public UpstreamServiceUnavailableException(String serviceName, Throwable cause) {
        super("UPSTREAM_SERVICE_UNAVAILABLE", HttpStatus.SERVICE_UNAVAILABLE,
                "Could not reach " + serviceName + " — please try again shortly");
        initCause(cause);
    }
}
