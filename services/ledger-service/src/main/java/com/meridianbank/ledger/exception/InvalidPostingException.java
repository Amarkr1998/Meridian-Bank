package com.meridianbank.ledger.exception;

import org.springframework.http.HttpStatus;

public class InvalidPostingException extends LedgerException {
    public InvalidPostingException(String message) {
        super("INVALID_POSTING", HttpStatus.BAD_REQUEST, message);
    }
}
