package com.meridianbank.ledger.repository;

import com.meridianbank.ledger.domain.LedgerEntry;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, UUID> {

    List<LedgerEntry> findByTransactionId(UUID transactionId);

    boolean existsByTransactionId(UUID transactionId);

    Page<LedgerEntry> findByAccountIdOrderByCreatedAtDesc(UUID accountId, Pageable pageable);

    /** Every transaction ever posted — the universe {@link com.meridianbank.ledger.reconciliation.ReconciliationService}
     *  iterates each run (Phase 12). Both sides of a posting share one transactionId, so this is
     *  exactly one row per real transfer regardless of table size. */
    @Query("SELECT DISTINCT e.transactionId FROM LedgerEntry e")
    List<UUID> findAllDistinctTransactionIds();
}
