package com.meridianbank.fraud.exception;

import org.springframework.http.HttpStatus;

public class InvalidAlertTransitionException extends FraudRiskException {
    public InvalidAlertTransitionException(String message) {
        super("INVALID_ALERT_TRANSITION", HttpStatus.CONFLICT, message);
    }
}
