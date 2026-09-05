package com.meridianbank.gateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "meridian.gateway.rate-limit")
public record RateLimitProperties(int maxRequestsPerWindow, int windowSeconds) {
}
