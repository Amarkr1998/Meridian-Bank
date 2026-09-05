package com.meridianbank.payment.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "meridian.account-service")
public record AccountServiceProperties(String baseUrl) {
}
