package com.meridianbank.account.exception;

import org.springframework.http.HttpStatus;

public class InvalidBeneficiaryVerificationCodeException extends AccountException {
    public InvalidBeneficiaryVerificationCodeException() {
        super("INVALID_VERIFICATION_CODE", HttpStatus.UNAUTHORIZED, "The verification code is incorrect");
    }
}
