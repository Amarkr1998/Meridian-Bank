package com.meridianbank.kyc.exception;

import org.springframework.http.HttpStatus;

public class InvalidVerificationCodeException extends KycException {
    public InvalidVerificationCodeException() {
        super("INVALID_VERIFICATION_CODE", HttpStatus.UNAUTHORIZED, "The verification code is incorrect");
    }
}
