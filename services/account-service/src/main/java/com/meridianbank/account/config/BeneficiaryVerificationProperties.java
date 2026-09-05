package com.meridianbank.account.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "meridian.account.beneficiary-verification")
public record BeneficiaryVerificationProperties(long otpTtlSeconds, int maxAttempts, boolean demoExposeOtp) {
}
