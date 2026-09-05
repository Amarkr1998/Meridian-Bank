package com.meridianbank.fraud.exception;

import org.springframework.http.HttpStatus;

/** Base type for fraud-risk-service business exceptions — same pattern as every other service. */
public abstract class FraudRiskException extends RuntimeException {

    private final String errorCode;
    private final HttpStatus httpStatus;

    protected FraudRiskException(String errorCode, HttpStatus httpStatus, String message) {
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
