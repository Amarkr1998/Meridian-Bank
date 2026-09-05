package com.meridianbank.account.exception;

import org.springframework.http.HttpStatus;

public class InvalidAccountRequestTransitionException extends AccountException {
    public InvalidAccountRequestTransitionException(String message) {
        super("INVALID_ACCOUNT_REQUEST_TRANSITION", HttpStatus.CONFLICT, message);
    }
}
