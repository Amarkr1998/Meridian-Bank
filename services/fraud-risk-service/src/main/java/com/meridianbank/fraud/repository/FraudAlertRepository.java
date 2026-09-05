package com.meridianbank.fraud.repository;

import com.meridianbank.fraud.domain.FraudAlert;
import com.meridianbank.fraud.domain.FraudAlertStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface FraudAlertRepository extends JpaRepository<FraudAlert, UUID> {

    Page<FraudAlert> findByStatus(FraudAlertStatus status, Pageable pageable);

    Page<FraudAlert> findByCustomerId(UUID customerId, Pageable pageable);

    long countByCustomerIdAndStatus(UUID customerId, FraudAlertStatus status);
}
