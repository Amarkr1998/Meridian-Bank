package com.meridianbank.account.repository;

import com.meridianbank.account.domain.Account;
import com.meridianbank.account.domain.AccountStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface AccountRepository extends JpaRepository<Account, UUID> {

    boolean existsByAccountNumber(String accountNumber);

    Optional<Account> findByAccountNumber(String accountNumber);

    Page<Account> findByCustomerId(UUID customerId, Pageable pageable);

    Page<Account> findByCustomerIdAndStatus(UUID customerId, AccountStatus status, Pageable pageable);

    Page<Account> findByStatus(AccountStatus status, Pageable pageable);
}
