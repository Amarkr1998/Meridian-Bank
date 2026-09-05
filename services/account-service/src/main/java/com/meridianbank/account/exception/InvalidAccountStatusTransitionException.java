package com.meridianbank.account.exception;

import org.springframework.http.HttpStatus;

public class InvalidAccountStatusTransitionException extends AccountException {
    public InvalidAccountStatusTransitionException(String message) {
        super("INVALID_ACCOUNT_STATUS_TRANSITION", HttpStatus.CONFLICT, message);
    }
}
