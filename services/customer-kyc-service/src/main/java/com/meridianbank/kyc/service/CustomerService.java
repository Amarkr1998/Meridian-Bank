package com.meridianbank.kyc.service;

import com.meridianbank.kyc.config.ContactVerificationProperties;
import com.meridianbank.kyc.domain.Customer;
import com.meridianbank.kyc.domain.CustomerStatus;
import com.meridianbank.kyc.domain.CustomerStatusHistory;
import com.meridianbank.kyc.exception.CustomerNotFoundException;
import com.meridianbank.kyc.repository.CustomerRepository;
import com.meridianbank.kyc.repository.CustomerStatusHistoryRepository;
import com.meridianbank.kyc.web.dto.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class CustomerService {

    private final CustomerRepository customerRepository;
    private final CustomerStatusHistoryRepository statusHistoryRepository;
    private final ContactVerificationService contactVerificationService;
    private final ContactVerificationProperties contactVerificationProperties;

    public CustomerService(CustomerRepository customerRepository,
                            CustomerStatusHistoryRepository statusHistoryRepository,
                            ContactVerificationService contactVerificationService,
                            ContactVerificationProperties contactVerificationProperties) {
        this.customerRepository = customerRepository;
        this.statusHistoryRepository = statusHistoryRepository;
        this.contactVerificationService = contactVerificationService;
        this.contactVerificationProperties = contactVerificationProperties;
    }

    @Transactional(readOnly = true)
    public Customer getOrThrow(UUID id) {
        return customerRepository.findById(id).orElseThrow(CustomerNotFoundException::new);
    }

    @Transactional
    public void verifyContact(UUID customerId, String otp) {
        Customer customer = getOrThrow(customerId);
        contactVerificationService.verify(customerId, otp);
        customer.setContactVerified(true);
        customerRepository.save(customer);
    }

    @Transactional
    public ResendVerificationResponse resendVerification(UUID customerId) {
        Customer customer = getOrThrow(customerId);
        ContactVerificationService.Challenge challenge = contactVerificationService.createChallenge(customer.getId());
        String devOtp = contactVerificationProperties.demoExposeOtp() ? challenge.otp() : null;
        return new ResendVerificationResponse(challenge.expiresInSeconds(), devOtp);
    }

    @Transactional
    public Customer updateProfile(UUID customerId, UpdateCustomerRequest request) {
        Customer customer = getOrThrow(customerId);
        customer.setPhone(request.phone());
        customer.setAddressLine1(request.addressLine1());
        customer.setAddressLine2(request.addressLine2());
        customer.setCity(request.city());
        customer.setState(request.state());
        customer.setPostalCode(request.postalCode());
        customer.setCountry(request.country());
        return customerRepository.save(customer);
    }

    @Transactional
    public Customer updateStatus(UUID customerId, UpdateCustomerStatusRequest request, UUID actorId) {
        Customer customer = getOrThrow(customerId);
        CustomerStatus oldStatus = customer.getStatus();
        customer.setStatus(request.status());
        customerRepository.save(customer);
        statusHistoryRepository.save(
                new CustomerStatusHistory(customerId, oldStatus, request.status(), request.reason(), actorId));
        return customer;
    }

    @Transactional(readOnly = true)
    public List<CustomerStatusHistoryResponse> getStatusHistory(UUID customerId) {
        return statusHistoryRepository.findByCustomerIdOrderByChangedAtDesc(customerId).stream()
                .map(CustomerStatusHistoryResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public Page<Customer> list(CustomerStatus status, Pageable pageable) {
        return status != null
                ? customerRepository.findByStatus(status, pageable)
                : customerRepository.findAll(pageable);
    }
}
