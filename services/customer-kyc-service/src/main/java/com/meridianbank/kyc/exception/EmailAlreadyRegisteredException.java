package com.meridianbank.kyc.exception;

import org.springframework.http.HttpStatus;

public class EmailAlreadyRegisteredException extends KycException {
    public EmailAlreadyRegisteredException() {
        super("EMAIL_ALREADY_REGISTERED", HttpStatus.CONFLICT, "An account with this email already exists");
    }
}
