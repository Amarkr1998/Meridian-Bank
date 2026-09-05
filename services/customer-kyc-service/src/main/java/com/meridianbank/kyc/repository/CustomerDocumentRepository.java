package com.meridianbank.kyc.repository;

import com.meridianbank.kyc.domain.CustomerDocument;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CustomerDocumentRepository extends JpaRepository<CustomerDocument, UUID> {

    List<CustomerDocument> findByKycRecordId(UUID kycRecordId);
}
