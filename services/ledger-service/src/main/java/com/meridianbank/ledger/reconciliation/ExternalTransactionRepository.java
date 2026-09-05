package com.meridianbank.ledger.reconciliation;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ExternalTransactionRepository extends JpaRepository<ExternalTransaction, UUID> {

    Optional<ExternalTransaction> findByReference(UUID reference);
}
