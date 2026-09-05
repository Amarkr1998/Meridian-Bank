package com.meridianbank.payment.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "meridian.customer-kyc-service")
public record CustomerKycServiceProperties(String baseUrl) {
}
