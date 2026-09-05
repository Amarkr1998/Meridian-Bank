package com.meridianbank.kyc.exception;

import org.springframework.http.HttpStatus;

public class InvalidKycTransitionException extends KycException {
    public InvalidKycTransitionException(String message) {
        super("INVALID_KYC_TRANSITION", HttpStatus.CONFLICT, message);
    }
}
