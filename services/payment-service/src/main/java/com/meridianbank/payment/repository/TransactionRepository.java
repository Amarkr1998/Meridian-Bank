package com.meridianbank.payment.repository;

import com.meridianbank.payment.domain.Transaction;
import com.meridianbank.payment.domain.TransactionStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface TransactionRepository extends JpaRepository<Transaction, UUID> {

    Optional<Transaction> findByCustomerIdAndIdempotencyKey(UUID customerId, String idempotencyKey);

    Page<Transaction> findByCustomerId(UUID customerId, Pageable pageable);

    Page<Transaction> findByCustomerIdAndStatus(UUID customerId, TransactionStatus status, Pageable pageable);

    Page<Transaction> findByStatus(TransactionStatus status, Pageable pageable);

    /** Running total for the daily-limit check — see payment-service/README.md on why this is not
     *  yet concurrency-hardened (that lands alongside ledger-service in Phase 7). */
    @Query("select coalesce(sum(t.amount), 0) from Transaction t where t.sourceAccountId = :accountId "
            + "and t.status = 'SUCCESS' and t.createdAt >= :since")
    BigDecimal sumSuccessfulAmountSince(@Param("accountId") UUID accountId, @Param("since") Instant since);
}
