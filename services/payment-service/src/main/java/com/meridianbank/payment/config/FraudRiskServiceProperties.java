package com.meridianbank.payment.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "meridian.fraud-risk-service")
public record FraudRiskServiceProperties(String baseUrl) {
}
