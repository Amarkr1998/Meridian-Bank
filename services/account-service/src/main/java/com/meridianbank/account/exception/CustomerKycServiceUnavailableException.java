package com.meridianbank.account.exception;

import org.springframework.http.HttpStatus;

public class CustomerKycServiceUnavailableException extends AccountException {
    public CustomerKycServiceUnavailableException(Throwable cause) {
        super("CUSTOMER_KYC_SERVICE_UNAVAILABLE", HttpStatus.SERVICE_UNAVAILABLE,
                "Could not reach the customer/KYC service — please try again shortly");
        initCause(cause);
    }
}
