package com.meridianbank.kyc.exception;

import org.springframework.http.HttpStatus;

public class KycAlreadyInProgressException extends KycException {
    public KycAlreadyInProgressException() {
        super("KYC_ALREADY_IN_PROGRESS", HttpStatus.CONFLICT,
                "A KYC submission is already pending or under review for this customer");
    }
}
