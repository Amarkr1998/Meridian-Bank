package com.meridianbank.kyc.approval;

import com.meridianbank.kyc.exception.KycException;
import org.springframework.http.HttpStatus;

public class InvalidApprovalTransitionException extends KycException {
    public InvalidApprovalTransitionException(String message) {
        super("INVALID_APPROVAL_TRANSITION", HttpStatus.CONFLICT, message);
    }
}
