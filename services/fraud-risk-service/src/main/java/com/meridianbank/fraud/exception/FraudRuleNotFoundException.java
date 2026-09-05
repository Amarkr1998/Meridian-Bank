package com.meridianbank.fraud.exception;

import org.springframework.http.HttpStatus;

public class FraudRuleNotFoundException extends FraudRiskException {
    public FraudRuleNotFoundException() {
        super("FRAUD_RULE_NOT_FOUND", HttpStatus.NOT_FOUND, "Fraud rule not found");
    }
}
