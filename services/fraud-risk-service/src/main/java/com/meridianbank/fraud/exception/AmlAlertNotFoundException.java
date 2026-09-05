package com.meridianbank.fraud.exception;

import org.springframework.http.HttpStatus;

public class AmlAlertNotFoundException extends FraudRiskException {
    public AmlAlertNotFoundException() {
        super("AML_ALERT_NOT_FOUND", HttpStatus.NOT_FOUND, "AML alert not found");
    }
}
