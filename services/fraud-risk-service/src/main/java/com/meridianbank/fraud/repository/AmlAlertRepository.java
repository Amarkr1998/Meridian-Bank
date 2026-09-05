package com.meridianbank.fraud.repository;

import com.meridianbank.fraud.domain.AmlAlert;
import com.meridianbank.fraud.domain.AmlAlertStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface AmlAlertRepository extends JpaRepository<AmlAlert, UUID> {

    Page<AmlAlert> findByStatus(AmlAlertStatus status, Pageable pageable);

    Page<AmlAlert> findByCustomerId(UUID customerId, Pageable pageable);

    long countByCustomerIdAndStatus(UUID customerId, AmlAlertStatus status);
}
