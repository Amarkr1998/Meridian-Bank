package com.meridianbank.kyc.outbox;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "meridian.outbox")
public record OutboxProperties(long pollIntervalMs, int batchSize, int maxAttempts,
                                long baseBackoffSeconds, long maxBackoffSeconds) {
}
