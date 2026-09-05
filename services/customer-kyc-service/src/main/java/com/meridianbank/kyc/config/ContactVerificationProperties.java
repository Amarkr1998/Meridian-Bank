package com.meridianbank.kyc.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "meridian.security.contact-verification")
public record ContactVerificationProperties(long otpTtlSeconds, int maxAttempts, boolean demoExposeOtp) {
}
