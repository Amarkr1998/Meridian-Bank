package com.meridianbank.ledger.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Must match auth-service's signing secret — this service only verifies tokens, never issues them. */
@ConfigurationProperties(prefix = "meridian.security.jwt")
public record JwtVerificationProperties(String secret) {
}
