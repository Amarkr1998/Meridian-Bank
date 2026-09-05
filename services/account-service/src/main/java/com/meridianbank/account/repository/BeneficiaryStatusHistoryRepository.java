package com.meridianbank.account.repository;

import com.meridianbank.account.domain.BeneficiaryStatusHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface BeneficiaryStatusHistoryRepository extends JpaRepository<BeneficiaryStatusHistory, UUID> {

    List<BeneficiaryStatusHistory> findByBeneficiaryIdOrderByChangedAtDesc(UUID beneficiaryId);
}
