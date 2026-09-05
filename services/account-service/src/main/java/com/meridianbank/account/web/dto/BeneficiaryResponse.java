package com.meridianbank.account.web.dto;

import com.meridianbank.account.domain.Account;
import com.meridianbank.account.domain.AccountStatus;
import com.meridianbank.account.domain.Beneficiary;
import com.meridianbank.account.domain.BeneficiaryStatus;

import java.time.Instant;
import java.util.UUID;

/**
 * {@code destinationAccountId}/{@code destinationAccountStatus} let payment-service (Phase 6)
 * reference and re-validate the target account without ever seeing the raw account number —
 * account-service resolves beneficiary → account internally since it owns both. Only the owning
 * customer (or staff) can reach this response at all (see BeneficiaryController), and even they
 * never see the raw number back — they already know it, they typed it in when adding this
 * beneficiary.
 */
public record BeneficiaryResponse(
        UUID id,
        String nickname,
        String beneficiaryName,
        String maskedBeneficiaryAccountNumber,
        BeneficiaryStatus status,
        Instant activatedAt,
        Instant createdAt,
        UUID destinationAccountId,
        AccountStatus destinationAccountStatus
) {
    public static BeneficiaryResponse from(Beneficiary b, Account destinationAccount) {
        return new BeneficiaryResponse(b.getId(), b.getNickname(), b.getBeneficiaryName(),
                mask(b.getBeneficiaryAccountNumber()), b.getStatus(), b.getActivatedAt(), b.getCreatedAt(),
                destinationAccount != null ? destinationAccount.getId() : null,
                destinationAccount != null ? destinationAccount.getStatus() : null);
    }

    private static String mask(String accountNumber) {
        if (accountNumber == null || accountNumber.length() <= 4) {
            return "****";
        }
        String lastFour = accountNumber.substring(accountNumber.length() - 4);
        return "*".repeat(accountNumber.length() - 4) + lastFour;
    }
}
