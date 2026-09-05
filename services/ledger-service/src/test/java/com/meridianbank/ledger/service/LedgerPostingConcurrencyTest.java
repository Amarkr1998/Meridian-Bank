package com.meridianbank.ledger.service;

import com.meridianbank.ledger.domain.Balance;
import com.meridianbank.ledger.exception.InsufficientBalanceException;
import com.meridianbank.ledger.repository.BalanceRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The actual proof of this phase's "concurrency" half: real concurrent transfers against real
 * Postgres (no mocks — a mocked repository can't demonstrate that a row lock works), showing the
 * pessimistic {@code SELECT ... FOR UPDATE} in LedgerPostingService genuinely prevents an
 * account from going negative under a race, rather than merely looking correct in single-threaded
 * tests. Deliberately NOT annotated @Transactional — that would serialize the test onto one
 * connection and defeat the entire point. See docs/adr/0008-concurrency-strategy.md.
 */
@SpringBootTest
@Testcontainers
class LedgerPostingConcurrencyTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private LedgerPostingService postingService;

    @Autowired
    private BalanceRepository balanceRepository;

    @Test
    void concurrentDebitsFromTheSameAccount_neverOverdraw() throws InterruptedException {
        UUID debitAccount = UUID.randomUUID();
        UUID creditAccount = UUID.randomUUID();

        // A real double-entry ledger can't create money from nothing — every credit needs a
        // matching debit somewhere (correctly proven by this same test failing with
        // InsufficientBalanceException when this seed used to go through a zero-balance
        // "funding" account via postingService.post()). Seeding a starting balance for the test
        // fixture is therefore a direct repository write, not a posting.
        seedBalance(debitAccount, "100.00");

        int attempts = 5;
        BigDecimal transferAmount = new BigDecimal("30.00"); // 5 x 30 = 150 > 100 available
        ExecutorService executor = Executors.newFixedThreadPool(attempts);
        CountDownLatch ready = new CountDownLatch(attempts);
        CountDownLatch go = new CountDownLatch(1);

        List<Future<Boolean>> futures;
        try {
            futures = IntStream.range(0, attempts)
                    .mapToObj(i -> executor.submit(() -> {
                        ready.countDown();
                        go.await();
                        try {
                            postingService.post(UUID.randomUUID(), debitAccount, creditAccount, transferAmount,
                                    "USD", "concurrency-test");
                            return true;
                        } catch (InsufficientBalanceException e) {
                            return false;
                        }
                    }))
                    .collect(Collectors.toList());

            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            go.countDown();
        } finally {
            executor.shutdown();
        }

        long succeeded = futures.stream().map(f -> {
            try {
                return f.get(10, TimeUnit.SECONDS);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }).filter(Boolean::booleanValue).count();

        // floor(100 / 30) = 3 transfers can be honored; the other 2 must be rejected, never
        // partially applied.
        assertThat(succeeded).isEqualTo(3);

        Balance finalBalance = balanceRepository.findById(debitAccount).orElseThrow();
        assertThat(finalBalance.getAvailableBalance()).isEqualByComparingTo("10.00");
        assertThat(finalBalance.getAvailableBalance()).isGreaterThanOrEqualTo(BigDecimal.ZERO);

        Balance creditBalance = balanceRepository.findById(creditAccount).orElseThrow();
        assertThat(creditBalance.getAvailableBalance()).isEqualByComparingTo("90.00");
    }

    @Test
    void concurrentOppositeDirectionTransfersBetweenSameTwoAccounts_doNotDeadlock() throws Exception {
        UUID accountA = UUID.randomUUID();
        UUID accountB = UUID.randomUUID();
        seedBalance(accountA, "500.00");
        seedBalance(accountB, "500.00");

        int rounds = 20;
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch go = new CountDownLatch(1);

        Future<?> aToB = executor.submit(() -> {
            try {
                go.await();
                for (int i = 0; i < rounds; i++) {
                    postingService.post(UUID.randomUUID(), accountA, accountB, BigDecimal.ONE, "USD", "a-to-b");
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        Future<?> bToA = executor.submit(() -> {
            try {
                go.await();
                for (int i = 0; i < rounds; i++) {
                    postingService.post(UUID.randomUUID(), accountB, accountA, BigDecimal.ONE, "USD", "b-to-a");
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        go.countDown();
        // If the fixed lock ordering in LedgerPostingService didn't prevent deadlocks, this would
        // hang until the test's own timeout rather than complete promptly.
        aToB.get(15, TimeUnit.SECONDS);
        bToA.get(15, TimeUnit.SECONDS);
        executor.shutdown();

        // Equal and opposite transfers net out to the original balances.
        assertThat(balanceRepository.findById(accountA).orElseThrow().getAvailableBalance())
                .isEqualByComparingTo("500.00");
        assertThat(balanceRepository.findById(accountB).orElseThrow().getAvailableBalance())
                .isEqualByComparingTo("500.00");
    }

    /** Test-fixture-only: a real double-entry ledger has no way to create balance from nothing —
     *  every credit needs a matching debit (see LedgerPostingService). Seeding a starting balance
     *  for a test therefore writes directly to the repository rather than going through post(). */
    private void seedBalance(UUID accountId, String amount) {
        Balance balance = new Balance(accountId, "USD");
        balance.credit(new BigDecimal(amount));
        balanceRepository.save(balance);
    }
}
