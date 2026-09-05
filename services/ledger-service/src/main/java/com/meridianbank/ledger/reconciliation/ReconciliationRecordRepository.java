package com.meridianbank.ledger.reconciliation;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ReconciliationRecordRepository extends JpaRepository<ReconciliationRecord, UUID> {

    Optional<ReconciliationRecord> findByTransactionId(UUID transactionId);

    List<ReconciliationRecord> findByStatus(ReconciliationStatus status);

    Page<ReconciliationRecord> findByStatus(ReconciliationStatus status, Pageable pageable);
}
