package com.meridianbank.audit.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Consumer-side retry/backoff policy — see AuditKafkaConfig and docs/architecture/kafka-architecture.md. */
@ConfigurationProperties(prefix = "meridian.audit-consumer")
public record AuditConsumerProperties(int maxAttempts, long baseBackoffMs, long maxBackoffMs) {
}
