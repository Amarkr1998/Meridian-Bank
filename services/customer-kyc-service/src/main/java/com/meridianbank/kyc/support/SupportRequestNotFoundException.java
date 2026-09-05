package com.meridianbank.kyc.support;

import com.meridianbank.kyc.exception.KycException;
import org.springframework.http.HttpStatus;

public class SupportRequestNotFoundException extends KycException {

    public SupportRequestNotFoundException() {
        super("SUPPORT_REQUEST_NOT_FOUND", HttpStatus.NOT_FOUND, "Support request not found");
    }
}
