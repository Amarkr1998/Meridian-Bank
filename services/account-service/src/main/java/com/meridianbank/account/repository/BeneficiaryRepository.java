package com.meridianbank.account.repository;

import com.meridianbank.account.domain.Beneficiary;
import com.meridianbank.account.domain.BeneficiaryStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface BeneficiaryRepository extends JpaRepository<Beneficiary, UUID> {

    boolean existsByCustomerIdAndBeneficiaryAccountNumber(UUID customerId, String beneficiaryAccountNumber);

    Page<Beneficiary> findByCustomerId(UUID customerId, Pageable pageable);

    Page<Beneficiary> findByCustomerIdAndStatus(UUID customerId, BeneficiaryStatus status, Pageable pageable);

    Page<Beneficiary> findByStatus(BeneficiaryStatus status, Pageable pageable);
}
