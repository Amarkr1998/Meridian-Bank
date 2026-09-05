package com.meridianbank.account.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "meridian.ledger-service")
public record LedgerServiceProperties(String baseUrl) {
}
