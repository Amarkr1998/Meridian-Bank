package com.meridianbank.kyc.exception;

import org.springframework.http.HttpStatus;

public class ContactNotVerifiedException extends KycException {
    public ContactNotVerifiedException() {
        super("CONTACT_NOT_VERIFIED", HttpStatus.CONFLICT,
                "Contact details must be verified before submitting KYC");
    }
}
