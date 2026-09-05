package com.meridianbank.payment.approval;

import com.meridianbank.payment.exception.PaymentException;
import org.springframework.http.HttpStatus;

public class InvalidApprovalTransitionException extends PaymentException {
    public InvalidApprovalTransitionException(String message) {
        super("INVALID_APPROVAL_TRANSITION", HttpStatus.CONFLICT, message);
    }
}
