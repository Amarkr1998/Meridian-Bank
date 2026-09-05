package com.meridianbank.account.web.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.meridianbank.account.client.LedgerServiceClient;
import com.meridianbank.account.domain.Account;
import com.meridianbank.account.domain.AccountStatus;
import com.meridianbank.account.domain.AccountType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * The account number is always masked in API responses, showing only the last 4 digits — see
 * docs/governance/governance-principles.md ("Account: ********4589"). The unmasked number is
 * never transmitted; payment-service resolves a destination account via
 * {@code BeneficiaryResponse.destinationAccountId} instead — see its README.
 *
 * <p>{@code availableBalance}/{@code ledgerBalance} come from ledger-service (Phase 7,
 * docs/adr/0007-double-entry-ledger.md) and are {@code null} if it couldn't be reached — a
 * balance-lookup failure degrades gracefully rather than breaking basic account viewing (see
 * LedgerServiceClient). Only populated on the single-account lookup, not the list endpoint — see
 * account-service/README.md on why enriching every row of a list would mean N sequential calls.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AccountResponse(
        UUID id,
        String maskedAccountNumber,
        AccountType accountType,
        AccountStatus status,
        String currency,
        BigDecimal perTransactionLimit,
        BigDecimal dailyLimit,
        Instant openedAt,
        Instant closedAt,
        BigDecimal availableBalance,
        BigDecimal ledgerBalance
) {
    public static AccountResponse from(Account a) {
        return from(a, null);
    }

    public static AccountResponse from(Account a, LedgerServiceClient.BalanceSummary balance) {
        return new AccountResponse(a.getId(), mask(a.getAccountNumber()), a.getAccountType(), a.getStatus(),
                a.getCurrency(), a.getPerTransactionLimit(), a.getDailyLimit(), a.getOpenedAt(), a.getClosedAt(),
                balance != null ? balance.availableBalance() : null, balance != null ? balance.ledgerBalance() : null);
    }

    private static String mask(String accountNumber) {
        if (accountNumber == null || accountNumber.length() <= 4) {
            return "****";
        }
        String lastFour = accountNumber.substring(accountNumber.length() - 4);
        return "*".repeat(accountNumber.length() - 4) + lastFour;
    }
}
