package com.meridianbank.ledger.reconciliation;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Synthesizes a locally-generated, deterministic stand-in for what an external system would
 * report for one internal ledger transaction — see docs/reconciliation/reconciliation-design.md
 * ("External records are always synthetic... never real external bank data or a live third-party
 * integration"). This is not randomness dressed up as realism: the outcome for a given
 * {@code transactionId} is a pure function of its own UUID bits, so it is reproducible and
 * testable, while still producing a believable mix of outcomes across many transactions:
 *
 * <ul>
 *   <li><b>70% — immediate match:</b> an {@link ExternalTransaction} row is created right now,
 *       with the identical amount/currency, immediately available. {@link ReconciliationService}
 *       will classify this transaction {@code MATCHED} the moment it next runs.</li>
 *   <li><b>20% — delayed match:</b> same identical amount/currency, but not "available" until
 *       {@code meridian.reconciliation.external-feed-delay-seconds} (default 120s) after
 *       generation — modeling a real settlement-timing difference. Until that time passes, the
 *       reconciliation job sees no usable counterpart and the record stays {@code PENDING}; once
 *       it passes, the very next run finds it and classifies {@code MATCHED} — a genuine
 *       PENDING → MATCHED transition driven by wall-clock time, not a second random roll.</li>
 *   <li><b>10% — mismatch:</b> immediately available, but with a deliberately wrong amount (a
 *       small, deterministic, non-zero offset), so {@link ReconciliationService} classifies
 *       {@code MISMATCHED} on the next run — demonstrating the control this whole feature exists
 *       to prove out, not just the happy path.</li>
 * </ul>
 *
 * <p>Idempotent: {@code external_transactions.reference} is unique on {@code transactionId}, so a
 * transaction's synthetic counterpart is generated exactly once, ever, regardless of how many
 * times {@link #generateIfAbsent} is called for it (the reconciliation job calls it every run
 * until a record leaves {@code PENDING}).
 */
@Component
public class ExternalFeedGenerator {

    private static final int MATCH_IMMEDIATE_UPPER_BOUND = 7;
    private static final int MATCH_DELAYED_UPPER_BOUND = 9;

    private final ExternalTransactionRepository repository;
    private final ReconciliationProperties properties;

    public ExternalFeedGenerator(ExternalTransactionRepository repository, ReconciliationProperties properties) {
        this.repository = repository;
        this.properties = properties;
    }

    @Transactional
    public ExternalTransaction generateIfAbsent(UUID transactionId, BigDecimal internalAmount, String currency) {
        return repository.findByReference(transactionId)
                .orElseGet(() -> repository.save(synthesize(transactionId, internalAmount, currency)));
    }

    private ExternalTransaction synthesize(UUID transactionId, BigDecimal internalAmount, String currency) {
        int bucket = classificationBucket(transactionId);
        Instant now = Instant.now();

        if (bucket < MATCH_IMMEDIATE_UPPER_BOUND) {
            return new ExternalTransaction(transactionId, internalAmount, currency, now);
        }
        if (bucket < MATCH_DELAYED_UPPER_BOUND) {
            return new ExternalTransaction(transactionId, internalAmount, currency,
                    now.plusSeconds(properties.externalFeedDelaySeconds()));
        }
        BigDecimal mismatchOffset = deterministicNonZeroOffset(transactionId);
        return new ExternalTransaction(transactionId, internalAmount.add(mismatchOffset), currency, now);
    }

    /** 0-9, uniform-ish, a pure function of the transaction id — see class Javadoc for the bands. */
    private int classificationBucket(UUID transactionId) {
        return (int) Math.floorMod(transactionId.getLeastSignificantBits(), 10L);
    }

    /** A reproducible, always-non-zero $1.00-$9.99 discrepancy, distinct per transaction. */
    private BigDecimal deterministicNonZeroOffset(UUID transactionId) {
        long cents = 100 + Math.floorMod(transactionId.getMostSignificantBits(), 900L);
        return BigDecimal.valueOf(cents, 2);
    }
}
