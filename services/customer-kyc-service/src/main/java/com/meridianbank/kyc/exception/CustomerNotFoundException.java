package com.meridianbank.kyc.exception;

import org.springframework.http.HttpStatus;

public class CustomerNotFoundException extends KycException {
    public CustomerNotFoundException() {
        super("CUSTOMER_NOT_FOUND", HttpStatus.NOT_FOUND, "Customer not found");
    }
}
