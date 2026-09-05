package com.meridianbank.auth.exception;

import org.springframework.http.HttpStatus;

public class MfaAttemptsExceededException extends AuthException {
    public MfaAttemptsExceededException() {
        super("MFA_ATTEMPTS_EXCEEDED", HttpStatus.TOO_MANY_REQUESTS,
                "Too many incorrect verification code attempts — please log in again");
    }
}
