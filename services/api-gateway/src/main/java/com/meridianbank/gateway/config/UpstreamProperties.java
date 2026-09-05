package com.meridianbank.gateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "meridian.gateway.upstream")
public record UpstreamProperties(int connectTimeoutMs, int readTimeoutMs) {
}
