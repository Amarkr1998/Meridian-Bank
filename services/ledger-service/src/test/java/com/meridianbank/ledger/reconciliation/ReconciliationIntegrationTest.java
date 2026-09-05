package com.meridianbank.ledger.reconciliation;

import com.meridianbank.ledger.domain.Balance;
import com.meridianbank.ledger.repository.BalanceRepository;
import com.meridianbank.ledger.service.LedgerPostingService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Real Postgres, real {@link LedgerPostingService} postings, real
 * {@link ReconciliationService#run()} — not mocks. Proves the full classification pipeline against
 * genuine ledger_entries rows, and, crucially, the PENDING → MATCHED transition that only happens
 * once wall-clock time actually passes {@code external-feed-delay-seconds} — see
 * {@link ExternalFeedGenerator}'s Javadoc for why that's deterministic rather than a second random
 * roll. {@code meridian.reconciliation.external-feed-delay-seconds} is shortened to 2s here so the
 * test doesn't wait the real 120s default.
 */
@SpringBootTest(properties = "meridian.reconciliation.external-feed-delay-seconds=2")
@Testcontainers
class ReconciliationIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private LedgerPostingService postingService;

    @Autowired
    private BalanceRepository balanceRepository;

    @Autowired
    private ReconciliationService reconciliationService;

    @Autowired
    private ReconciliationRecordRepository reconciliationRecordRepository;

    private void seedBalance(UUID accountId, String amount) {
        Balance balance = new Balance(accountId, "USD");
        balance.credit(new BigDecimal(amount));
        balanceRepository.save(balance);
    }

    /** Crafts a transactionId landing in a specific {@link ExternalFeedGenerator} bucket — see its
     *  Javadoc: least-significant-bits mod 10 decides immediate-match (0-6) / delayed-match (7-8)
     *  / mismatch (9). */
    private UUID transactionIdInBucket(int bucket) {
        return new UUID(UUID.randomUUID().getMostSignificantBits(), bucket);
    }

    @Test
    void run_realLedgerTransactions_classifiesAllThreeOutcomesCorrectly() {
        UUID debitAccount = UUID.randomUUID();
        UUID creditAccount = UUID.randomUUID();
        seedBalance(debitAccount, "10000.00");

        UUID matchTxn = transactionIdInBucket(0);
        UUID mismatchTxn = transactionIdInBucket(9);
        UUID pendingTxn = transactionIdInBucket(7);

        postingService.post(matchTxn, debitAccount, creditAccount, new BigDecimal("50.00"), "USD", "test-match");
        postingService.post(mismatchTxn, debitAccount, creditAccount, new BigDecimal("75.00"), "USD", "test-mismatch");
        postingService.post(pendingTxn, debitAccount, creditAccount, new BigDecimal("25.00"), "USD", "test-pending");

        ReconciliationService.RunSummary firstRun = reconciliationService.run();
        assertThat(firstRun.totalTransactions()).isEqualTo(3);
        assertThat(firstRun.matched()).isEqualTo(1);
        assertThat(firstRun.mismatched()).isEqualTo(1);
        assertThat(firstRun.pending()).isEqualTo(1);

        ReconciliationRecord matchedRecord = reconciliationRecordRepository.findByTransactionId(matchTxn).orElseThrow();
        assertThat(matchedRecord.getStatus()).isEqualTo(ReconciliationStatus.MATCHED);
        assertThat(matchedRecord.getExternalAmount()).isEqualByComparingTo("50.00");

        ReconciliationRecord mismatchedRecord = reconciliationRecordRepository.findByTransactionId(mismatchTxn)
                .orElseThrow();
        assertThat(mismatchedRecord.getStatus()).isEqualTo(ReconciliationStatus.MISMATCHED);
        assertThat(mismatchedRecord.getExternalAmount()).isNotEqualByComparingTo("75.00");
        assertThat(mismatchedRecord.getMismatchReason()).isNotBlank();

        ReconciliationRecord pendingRecord = reconciliationRecordRepository.findByTransactionId(pendingTxn)
                .orElseThrow();
        assertThat(pendingRecord.getStatus()).isEqualTo(ReconciliationStatus.PENDING);

        // Wait past the 2s synthetic external-feed delay, then re-run: the PENDING record must now
        // resolve to MATCHED, purely because wall-clock time passed — no re-generation involved
        // (the external_transactions row was already created on the first run).
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            reconciliationService.run();
            ReconciliationRecord nowMatched = reconciliationRecordRepository.findByTransactionId(pendingTxn)
                    .orElseThrow();
            assertThat(nowMatched.getStatus()).isEqualTo(ReconciliationStatus.MATCHED);
            assertThat(nowMatched.getExternalAmount()).isEqualByComparingTo("25.00");
        });
    }

    @Test
    void run_isIdempotentAcrossReruns_matchedRecordIsNeverReprocessedOrDuplicated() {
        UUID debitAccount = UUID.randomUUID();
        UUID creditAccount = UUID.randomUUID();
        seedBalance(debitAccount, "1000.00");
        UUID txn = transactionIdInBucket(0);
        postingService.post(txn, debitAccount, creditAccount, new BigDecimal("10.00"), "USD", "idempotency-check");

        reconciliationService.run();
        long countAfterFirst = reconciliationRecordRepository.findAll().stream()
                .filter(r -> r.getTransactionId().equals(txn)).count();
        reconciliationService.run();
        long countAfterSecond = reconciliationRecordRepository.findAll().stream()
                .filter(r -> r.getTransactionId().equals(txn)).count();

        assertThat(countAfterFirst).isEqualTo(1);
        assertThat(countAfterSecond).isEqualTo(1);
    }
}
