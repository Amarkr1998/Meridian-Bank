package com.meridianbank.account.exception;

import org.springframework.http.HttpStatus;

public class BeneficiaryVerificationAttemptsExceededException extends AccountException {
    public BeneficiaryVerificationAttemptsExceededException() {
        super("VERIFICATION_ATTEMPTS_EXCEEDED", HttpStatus.TOO_MANY_REQUESTS,
                "Too many incorrect verification code attempts — please request a new one");
    }
}
