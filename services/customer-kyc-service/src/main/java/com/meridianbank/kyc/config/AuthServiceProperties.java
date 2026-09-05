package com.meridianbank.kyc.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "meridian.auth-service")
public record AuthServiceProperties(String baseUrl) {
}
