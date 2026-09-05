package com.meridianbank.kyc.service;

import com.meridianbank.kyc.audit.AuditAction;
import com.meridianbank.kyc.audit.AuditEventPublisher;
import com.meridianbank.kyc.client.AuthServiceClient;
import com.meridianbank.kyc.config.ContactVerificationProperties;
import com.meridianbank.kyc.domain.Customer;
import com.meridianbank.kyc.exception.EmailAlreadyRegisteredException;
import com.meridianbank.kyc.outbox.OutboxWriter;
import com.meridianbank.kyc.repository.CustomerRepository;
import com.meridianbank.kyc.web.dto.RegisterCustomerRequest;
import com.meridianbank.kyc.web.dto.RegisterCustomerResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Registration spans two services: auth-service owns the login identity (email + password +
 * role), this service owns the customer profile. The same id is used in both places — see
 * AuthServiceClient and docs/architecture/onboarding-flow.md. If the local existence check
 * passes but auth-service still rejects as a duplicate (a benign race between the two checks),
 * that failure is surfaced as the same EMAIL_ALREADY_REGISTERED error.
 */
@Service
public class CustomerRegistrationService {

    private final CustomerRepository customerRepository;
    private final AuthServiceClient authServiceClient;
    private final ContactVerificationService contactVerificationService;
    private final ContactVerificationProperties contactVerificationProperties;
    private final OutboxWriter outboxWriter;
    private final AuditEventPublisher auditEventPublisher;

    public CustomerRegistrationService(CustomerRepository customerRepository,
                                        AuthServiceClient authServiceClient,
                                        ContactVerificationService contactVerificationService,
                                        ContactVerificationProperties contactVerificationProperties,
                                        OutboxWriter outboxWriter, AuditEventPublisher auditEventPublisher) {
        this.customerRepository = customerRepository;
        this.authServiceClient = authServiceClient;
        this.contactVerificationService = contactVerificationService;
        this.contactVerificationProperties = contactVerificationProperties;
        this.outboxWriter = outboxWriter;
        this.auditEventPublisher = auditEventPublisher;
    }

    @Transactional
    public RegisterCustomerResponse register(RegisterCustomerRequest request) {
        String email = request.email().trim().toLowerCase();
        if (customerRepository.existsByEmail(email)) {
            throw new EmailAlreadyRegisteredException();
        }

        AuthServiceClient.CreatedIdentity identity = authServiceClient.registerIdentity(email, request.password());

        Customer customer = new Customer(identity.id(), email, request.firstName(), request.lastName(),
                request.dateOfBirth(), request.phone(), request.addressLine1(), request.addressLine2(),
                request.city(), request.state(), request.postalCode(), request.country());
        customerRepository.save(customer);

        ContactVerificationService.Challenge challenge = contactVerificationService.createChallenge(customer.getId());
        String devOtp = contactVerificationProperties.demoExposeOtp() ? challenge.otp() : null;

        outboxWriter.write("customer.created", "customer", customer.getId(),
                new CustomerCreatedPayload(customer.getId(), customer.getEmail(), customer.getFirstName(),
                        customer.getLastName()));
        auditEventPublisher.record(AuditAction.CUSTOMER_REGISTERED, "customer", customer.getId(), customer.getId(),
                "CUSTOMER", "SUCCESS", "Self-service registration");

        return new RegisterCustomerResponse(customer.getId(), customer.getEmail(), false,
                challenge.expiresInSeconds(), devOtp);
    }

    private record CustomerCreatedPayload(UUID customerId, String email, String firstName, String lastName) {
    }
}
