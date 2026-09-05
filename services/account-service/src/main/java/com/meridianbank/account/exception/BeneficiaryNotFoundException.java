package com.meridianbank.account.exception;

import org.springframework.http.HttpStatus;

public class BeneficiaryNotFoundException extends AccountException {
    public BeneficiaryNotFoundException() {
        super("BENEFICIARY_NOT_FOUND", HttpStatus.NOT_FOUND, "Beneficiary not found");
    }
}
