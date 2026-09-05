package com.meridianbank.account.approval;

import com.meridianbank.account.exception.AccountException;
import org.springframework.http.HttpStatus;

public class InvalidApprovalTransitionException extends AccountException {
    public InvalidApprovalTransitionException(String message) {
        super("INVALID_APPROVAL_TRANSITION", HttpStatus.CONFLICT, message);
    }
}
