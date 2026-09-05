package com.meridianbank.fraud.approval;

import com.meridianbank.fraud.exception.FraudRiskException;
import org.springframework.http.HttpStatus;

public class InvalidApprovalTransitionException extends FraudRiskException {
    public InvalidApprovalTransitionException(String message) {
        super("INVALID_APPROVAL_TRANSITION", HttpStatus.CONFLICT, message);
    }
}
