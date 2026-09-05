package com.meridianbank.kyc.repository;

import com.meridianbank.kyc.domain.CustomerStatusHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CustomerStatusHistoryRepository extends JpaRepository<CustomerStatusHistory, UUID> {

    List<CustomerStatusHistory> findByCustomerIdOrderByChangedAtDesc(UUID customerId);
}
