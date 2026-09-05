package com.meridianbank.account.repository;

import com.meridianbank.account.domain.AccountOpeningRequest;
import com.meridianbank.account.domain.AccountOpeningStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AccountOpeningRequestRepository extends JpaRepository<AccountOpeningRequest, UUID> {

    List<AccountOpeningRequest> findByCustomerIdOrderByRequestedAtDesc(UUID customerId);

    Page<AccountOpeningRequest> findByStatus(AccountOpeningStatus status, Pageable pageable);

    boolean existsByCustomerIdAndStatusIn(UUID customerId, List<AccountOpeningStatus> statuses);
}
