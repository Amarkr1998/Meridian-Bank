package com.meridianbank.notification.exception;

import org.springframework.http.HttpStatus;

/** Base type for notification-service business exceptions — same pattern as every other service. */
public abstract class NotificationServiceException extends RuntimeException {

    private final String errorCode;
    private final HttpStatus httpStatus;

    protected NotificationServiceException(String errorCode, HttpStatus httpStatus, String message) {
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
