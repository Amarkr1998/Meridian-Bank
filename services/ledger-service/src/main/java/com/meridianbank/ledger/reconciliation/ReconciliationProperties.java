package com.meridianbank.ledger.reconciliation;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** See ReconciliationJob and ExternalFeedGenerator's Javadoc. */
@ConfigurationProperties(prefix = "meridian.reconciliation")
public record ReconciliationProperties(long pollIntervalMs, long externalFeedDelaySeconds) {
}
