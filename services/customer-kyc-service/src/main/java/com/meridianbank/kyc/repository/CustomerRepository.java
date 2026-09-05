package com.meridianbank.kyc.repository;

import com.meridianbank.kyc.domain.Customer;
import com.meridianbank.kyc.domain.CustomerStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface CustomerRepository extends JpaRepository<Customer, UUID> {

    Optional<Customer> findByEmail(String email);

    boolean existsByEmail(String email);

    Page<Customer> findByStatus(CustomerStatus status, Pageable pageable);
}
