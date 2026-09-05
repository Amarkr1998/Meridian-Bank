package com.meridianbank.ledger.reconciliation;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Proves the synthetic feed is a pure, reproducible function of the transaction id — see the
 *  class Javadoc for the 70/20/10 band definitions this test pins down concretely. */
@ExtendWith(MockitoExtension.class)
class ExternalFeedGeneratorTest {

    @Mock private ExternalTransactionRepository repository;

    private ExternalFeedGenerator generator;

    @BeforeEach
    void setUp() {
        generator = new ExternalFeedGenerator(repository, new ReconciliationProperties(60_000, 120));
        lenient().when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    /** {@code getLeastSignificantBits() mod 10 == 0} lands in the immediate-match band (0-6). */
    private UUID idInBucket(long bucket) {
        return new UUID(0L, bucket);
    }

    @Test
    void generateIfAbsent_bucketZero_isAnImmediateExactMatch() {
        UUID transactionId = idInBucket(0);
        when(repository.findByReference(transactionId)).thenReturn(Optional.empty());

        ExternalTransaction result = generator.generateIfAbsent(transactionId, new BigDecimal("100.00"), "USD");

        assertThat(result.getAmount()).isEqualByComparingTo("100.00");
        assertThat(result.getCurrency()).isEqualTo("USD");
        assertThat(result.isAvailable(Instant.now())).isTrue();
    }

    @Test
    void generateIfAbsent_bucketSeven_isADelayedButExactMatch() {
        UUID transactionId = idInBucket(7);
        when(repository.findByReference(transactionId)).thenReturn(Optional.empty());

        ExternalTransaction result = generator.generateIfAbsent(transactionId, new BigDecimal("100.00"), "USD");

        assertThat(result.getAmount()).isEqualByComparingTo("100.00");
        assertThat(result.isAvailable(Instant.now())).isFalse();
        assertThat(result.isAvailable(Instant.now().plusSeconds(121))).isTrue();
    }

    @Test
    void generateIfAbsent_bucketNine_isAnImmediateButWrongAmount() {
        UUID transactionId = idInBucket(9);
        when(repository.findByReference(transactionId)).thenReturn(Optional.empty());

        ExternalTransaction result = generator.generateIfAbsent(transactionId, new BigDecimal("100.00"), "USD");

        assertThat(result.isAvailable(Instant.now())).isTrue();
        assertThat(result.getAmount()).isNotEqualByComparingTo("100.00");
    }

    @Test
    void generateIfAbsent_calledTwiceForSameTransaction_isIdempotent() {
        UUID transactionId = idInBucket(0);
        ExternalTransaction existing = new ExternalTransaction(transactionId, new BigDecimal("50.00"), "USD",
                Instant.now());
        when(repository.findByReference(transactionId)).thenReturn(Optional.of(existing));

        ExternalTransaction result = generator.generateIfAbsent(transactionId, new BigDecimal("999.00"), "USD");

        assertThat(result).isSameAs(existing);
        verify(repository, never()).save(any());
    }

    @Test
    void classification_isDeterministic_sameTransactionIdAlwaysProducesTheSameOutcome() {
        UUID transactionId = UUID.randomUUID();
        when(repository.findByReference(transactionId)).thenReturn(Optional.empty());

        // Both calls independently synthesize (repository is mocked, so the second call re-derives
        // rather than reusing the first's persisted row) — the amount offset and the immediate-vs-
        // delayed banding must agree, since both are pure functions of the transaction id alone.
        // (availableAt itself is not compared directly — it's derived from Instant.now() at
        // generation time, so two independently-synthesized rows differ by nanoseconds even when
        // deterministic in every way that matters.)
        ExternalTransaction first = generator.generateIfAbsent(transactionId, new BigDecimal("42.00"), "USD");
        boolean firstWasImmediate = first.isAvailable(first.getGeneratedAt());
        ExternalTransaction second = generator.generateIfAbsent(transactionId, new BigDecimal("42.00"), "USD");
        boolean secondWasImmediate = second.isAvailable(second.getGeneratedAt());

        assertThat(second.getAmount()).isEqualByComparingTo(first.getAmount());
        assertThat(secondWasImmediate).isEqualTo(firstWasImmediate);
    }
}
