package com.meridianbank.kyc.support;

import com.meridianbank.kyc.exception.KycException;
import org.springframework.http.HttpStatus;

public class InvalidSupportRequestTransitionException extends KycException {

    public InvalidSupportRequestTransitionException(String message) {
        super("INVALID_SUPPORT_REQUEST_TRANSITION", HttpStatus.CONFLICT, message);
    }
}
