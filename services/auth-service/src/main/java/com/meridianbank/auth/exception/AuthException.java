package com.meridianbank.auth.exception;

import org.springframework.http.HttpStatus;

/**
 * Base type for auth-service business exceptions. Each carries a stable machine-readable
 * {@code errorCode} and the HTTP status it maps to in the standard error envelope
 * (see docs/api/api-governance.md).
 */
public abstract class AuthException extends RuntimeException {

    private final String errorCode;
    private final HttpStatus httpStatus;

    protected AuthException(String errorCode, HttpStatus httpStatus, String message) {
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
