package com.meridianbank.kyc.repository;

import com.meridianbank.kyc.domain.KycRecord;
import com.meridianbank.kyc.domain.KycStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface KycRecordRepository extends JpaRepository<KycRecord, UUID> {

    List<KycRecord> findByCustomerIdOrderBySubmittedAtDesc(UUID customerId);

    Page<KycRecord> findByStatus(KycStatus status, Pageable pageable);

    boolean existsByCustomerIdAndStatusIn(UUID customerId, List<KycStatus> statuses);
}
