package com.meridianbank.account.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "meridian.customer-kyc-service")
public record CustomerKycServiceProperties(String baseUrl) {
}
