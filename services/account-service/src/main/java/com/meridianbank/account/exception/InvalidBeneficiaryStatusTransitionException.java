package com.meridianbank.account.exception;

import org.springframework.http.HttpStatus;

public class InvalidBeneficiaryStatusTransitionException extends AccountException {
    public InvalidBeneficiaryStatusTransitionException(String message) {
        super("INVALID_BENEFICIARY_STATUS_TRANSITION", HttpStatus.CONFLICT, message);
    }
}
