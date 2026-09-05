package com.meridianbank.account.exception;

import org.springframework.http.HttpStatus;

public class DuplicateBeneficiaryException extends AccountException {
    public DuplicateBeneficiaryException() {
        super("DUPLICATE_BENEFICIARY", HttpStatus.CONFLICT,
                "This account is already in your beneficiary list");
    }
}
