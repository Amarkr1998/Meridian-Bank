package com.meridianbank.payment.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "meridian.ledger-service")
public record LedgerServiceProperties(String baseUrl) {
}
