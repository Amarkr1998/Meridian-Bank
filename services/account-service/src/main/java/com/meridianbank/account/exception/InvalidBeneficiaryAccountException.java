package com.meridianbank.account.exception;

import org.springframework.http.HttpStatus;

public class InvalidBeneficiaryAccountException extends AccountException {
    public InvalidBeneficiaryAccountException() {
        super("INVALID_BENEFICIARY_ACCOUNT", HttpStatus.CONFLICT,
                "The beneficiary account number does not exist or is not active");
    }
}
