package com.meridianbank.gateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "meridian.gateway.cors")
public record GatewayCorsProperties(List<String> allowedOrigins) {
}
