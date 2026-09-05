package com.meridianbank.ledger.exception;

import org.springframework.http.HttpStatus;

/** Base type for ledger-service business exceptions — same pattern as every other service. */
public abstract class LedgerException extends RuntimeException {

    private final String errorCode;
    private final HttpStatus httpStatus;

    protected LedgerException(String errorCode, HttpStatus httpStatus, String message) {
        super(message);
        this.errorCode = errorCode;
        this.httpStatus = httpStatus;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public HttpStatus getHttpStatus() {
        return httpStatus;
    }
}
