package com.meridianbank.kyc.exception;

import org.springframework.http.HttpStatus;

public class VerificationAttemptsExceededException extends KycException {
    public VerificationAttemptsExceededException() {
        super("VERIFICATION_ATTEMPTS_EXCEEDED", HttpStatus.TOO_MANY_REQUESTS,
                "Too many incorrect verification code attempts — please request a new one");
    }
}
