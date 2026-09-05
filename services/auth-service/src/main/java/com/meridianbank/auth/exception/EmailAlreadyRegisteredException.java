package com.meridianbank.auth.exception;

import org.springframework.http.HttpStatus;

public class EmailAlreadyRegisteredException extends AuthException {
    public EmailAlreadyRegisteredException() {
        super("EMAIL_ALREADY_REGISTERED", HttpStatus.CONFLICT,
                "An account with this email already exists");
    }
}
