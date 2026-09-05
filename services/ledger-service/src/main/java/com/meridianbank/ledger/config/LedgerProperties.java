package com.meridianbank.ledger.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Every account in this demo uses a single default currency (matching account-service's own
 *  default) — see ledger-service/README.md on why real multi-currency support is out of scope. */
@ConfigurationProperties(prefix = "meridian.ledger")
public record LedgerProperties(String defaultCurrency) {
}
