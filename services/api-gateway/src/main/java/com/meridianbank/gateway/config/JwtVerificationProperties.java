package com.meridianbank.gateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "meridian.security.jwt")
public record JwtVerificationProperties(String secret) {
}
