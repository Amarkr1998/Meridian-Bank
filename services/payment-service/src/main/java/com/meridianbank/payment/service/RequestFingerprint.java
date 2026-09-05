package com.meridianbank.payment.service;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;

/** Identifies "same semantic request" for idempotency-key-reuse detection — see IdempotencyService. */
public final class RequestFingerprint {

    private RequestFingerprint() {
    }

    public static String of(UUID sourceAccountId, UUID beneficiaryId, BigDecimal amount, String currency) {
        String normalized = sourceAccountId + "|" + beneficiaryId + "|" + amount.stripTrailingZeros().toPlainString()
                + "|" + currency.toUpperCase();
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(normalized.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
