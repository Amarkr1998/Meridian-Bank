package com.meridianbank.kyc.exception;

import org.springframework.http.HttpStatus;

/** Base type for customer-kyc-service business exceptions. See auth-service's AuthException for
 *  the same pattern — each carries a stable machine-readable errorCode and its HTTP status. */
public abstract class KycException extends RuntimeException {

    private final String errorCode;
    private final HttpStatus httpStatus;

    protected KycException(String errorCode, HttpStatus httpStatus, String message) {
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
