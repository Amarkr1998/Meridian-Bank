package com.meridianbank.notification.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Consumer-side retry/backoff policy — see NotificationKafkaConfig and docs/architecture/kafka-architecture.md. */
@ConfigurationProperties(prefix = "meridian.notification-consumer")
public record NotificationConsumerProperties(int maxAttempts, long baseBackoffMs, long maxBackoffMs) {
}
