package com.meridianbank.account.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;

/** Transaction limits are configurable data, not hardcoded constants — see
 *  docs/governance/governance-principles.md. These are the defaults applied at account opening;
 *  staff can override per-account via PATCH /api/v1/accounts/{id}/limits. */
@ConfigurationProperties(prefix = "meridian.account")
public record AccountDefaultsProperties(String defaultCurrency, BigDecimal defaultPerTransactionLimit,
                                         BigDecimal defaultDailyLimit) {
}
