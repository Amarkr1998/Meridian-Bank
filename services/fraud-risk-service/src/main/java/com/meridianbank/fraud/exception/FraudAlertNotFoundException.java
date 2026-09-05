package com.meridianbank.fraud.exception;

import org.springframework.http.HttpStatus;

public class FraudAlertNotFoundException extends FraudRiskException {
    public FraudAlertNotFoundException() {
        super("FRAUD_ALERT_NOT_FOUND", HttpStatus.NOT_FOUND, "Fraud alert not found");
    }
}
