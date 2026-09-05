package com.meridianbank.payment.repository;

import com.meridianbank.payment.domain.TransactionStatusHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface TransactionStatusHistoryRepository extends JpaRepository<TransactionStatusHistory, UUID> {

    List<TransactionStatusHistory> findByTransactionIdOrderByChangedAtAsc(UUID transactionId);
}
