package com.meridianbank.account.exception;

import org.springframework.http.HttpStatus;

public class SelfBeneficiaryNotAllowedException extends AccountException {
    public SelfBeneficiaryNotAllowedException() {
        super("SELF_BENEFICIARY_NOT_ALLOWED", HttpStatus.CONFLICT,
                "You cannot add your own account as a beneficiary");
    }
}
