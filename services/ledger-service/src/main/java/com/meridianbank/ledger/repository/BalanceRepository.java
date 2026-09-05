package com.meridianbank.ledger.repository;

import com.meridianbank.ledger.domain.Balance;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface BalanceRepository extends JpaRepository<Balance, UUID> {

    /**
     * Acquires a {@code SELECT ... FOR UPDATE} row lock — the core of this service's concurrency
     * strategy (see docs/adr/0008-concurrency-strategy.md and LedgerPostingService, which always
     * locks the two accounts in a fixed, deadlock-safe order).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from Balance b where b.accountId = :accountId")
    Optional<Balance> findByIdForUpdate(@Param("accountId") UUID accountId);

    /**
     * Atomic upsert-if-absent via {@code ON CONFLICT DO NOTHING} — deliberately not a
     * catch-and-retry around a plain INSERT: a unique-constraint violation would abort the
     * enclosing Postgres transaction, making in-transaction recovery unreliable (the same
     * reasoning documented in payment-service's PaymentService.createPayment). This native upsert
     * never throws on a concurrent duplicate, so it's safe to call unconditionally before the
     * locking read above.
     */
    @Modifying
    @Query(value = "insert into balances (account_id, available_balance, ledger_balance, currency, updated_at, version) "
            + "values (:accountId, 0, 0, :currency, now(), 0) on conflict (account_id) do nothing", nativeQuery = true)
    void ensureExists(@Param("accountId") UUID accountId, @Param("currency") String currency);
}
