package com.meridianbank.payment.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** See docs/adr/0006-idempotency.md and IdempotencyService. The in-progress TTL is short so a
 *  crash mid-processing doesn't permanently wedge a key; the completed TTL is long so a client
 *  retrying hours later still gets the original result rather than reprocessing. */
@ConfigurationProperties(prefix = "meridian.payment.idempotency")
public record IdempotencyProperties(long inProgressTtlSeconds, long completedTtlSeconds) {
}
