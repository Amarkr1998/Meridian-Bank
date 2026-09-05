package com.meridianbank.account.exception;

import org.springframework.http.HttpStatus;

/** Base type for account-service business exceptions — see auth-service's AuthException for the
 *  same pattern: each carries a stable machine-readable errorCode and its HTTP status. */
public abstract class AccountException extends RuntimeException {

    private final String errorCode;
    private final HttpStatus httpStatus;

    protected AccountException(String errorCode, HttpStatus httpStatus, String message) {
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
