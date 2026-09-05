package com.meridianbank.kyc.support;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface SupportRequestRepository extends JpaRepository<SupportRequest, UUID> {

    Page<SupportRequest> findByCustomerId(UUID customerId, Pageable pageable);

    Page<SupportRequest> findByStatus(SupportRequestStatus status, Pageable pageable);

    Page<SupportRequest> findByCustomerIdAndStatus(UUID customerId, SupportRequestStatus status, Pageable pageable);
}
