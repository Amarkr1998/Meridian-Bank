package com.meridianbank.account.exception;

import org.springframework.http.HttpStatus;

public class CustomerKycNotVerifiedException extends AccountException {
    public CustomerKycNotVerifiedException() {
        super("KYC_NOT_VERIFIED", HttpStatus.CONFLICT,
                "Customer must have verified KYC before an account can be requested");
    }
}
