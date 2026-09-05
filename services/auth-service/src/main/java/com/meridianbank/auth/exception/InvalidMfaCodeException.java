package com.meridianbank.auth.exception;

import org.springframework.http.HttpStatus;

public class InvalidMfaCodeException extends AuthException {
    public InvalidMfaCodeException() {
        super("INVALID_MFA_CODE", HttpStatus.UNAUTHORIZED, "The verification code is incorrect");
    }
}
