package com.meridianbank.account.service;

import com.meridianbank.account.audit.AuditAction;
import com.meridianbank.account.audit.AuditEventPublisher;
import com.meridianbank.account.client.CustomerKycServiceClient;
import com.meridianbank.account.domain.Account;
import com.meridianbank.account.domain.AccountOpeningRequest;
import com.meridianbank.account.domain.AccountOpeningStatus;
import com.meridianbank.account.domain.AccountType;
import com.meridianbank.account.exception.*;
import com.meridianbank.account.outbox.OutboxWriter;
import com.meridianbank.account.repository.AccountOpeningRequestRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * ACCOUNT_REQUESTED → UNDER_REVIEW → APPROVED (creates the Account) / REJECTED. Never automatic:
 * see docs/architecture/onboarding-flow.md ("Account opening is never automatic on registration").
 */
@Service
public class AccountOpeningService {

    private final AccountOpeningRequestRepository requestRepository;
    private final CustomerKycServiceClient customerKycServiceClient;
    private final AccountService accountService;
    private final OutboxWriter outboxWriter;
    private final AuditEventPublisher auditEventPublisher;

    public AccountOpeningService(AccountOpeningRequestRepository requestRepository,
                                  CustomerKycServiceClient customerKycServiceClient,
                                  AccountService accountService, OutboxWriter outboxWriter,
                                  AuditEventPublisher auditEventPublisher) {
        this.requestRepository = requestRepository;
        this.customerKycServiceClient = customerKycServiceClient;
        this.accountService = accountService;
        this.outboxWriter = outboxWriter;
        this.auditEventPublisher = auditEventPublisher;
    }

    @Transactional
    public AccountOpeningRequest submit(UUID customerId, AccountType accountType, String bearerToken) {
        if (requestRepository.existsByCustomerIdAndStatusIn(customerId,
                List.of(AccountOpeningStatus.ACCOUNT_REQUESTED, AccountOpeningStatus.UNDER_REVIEW))) {
            throw new AccountRequestAlreadyInProgressException();
        }
        if (!customerKycServiceClient.hasVerifiedKyc(customerId, bearerToken)) {
            throw new CustomerKycNotVerifiedException();
        }

        AccountOpeningRequest request = new AccountOpeningRequest(customerId, accountType);
        AccountOpeningRequest saved = requestRepository.save(request);
        outboxWriter.write("account.created", "account_opening_request", saved.getId(),
                new AccountCreatedPayload(saved.getId(), saved.getCustomerId(), saved.getAccountType()));
        return saved;
    }

    @Transactional
    public AccountOpeningRequest startReview(UUID requestId, UUID reviewerId) {
        AccountOpeningRequest request = getOrThrow(requestId);
        if (request.getStatus() != AccountOpeningStatus.ACCOUNT_REQUESTED) {
            throw new InvalidAccountRequestTransitionException(
                    "Only a REQUESTED submission can be claimed for review (current status: " + request.getStatus() + ")");
        }
        request.startReview(reviewerId);
        return requestRepository.save(request);
    }

    @Transactional
    public AccountOpeningRequest approve(UUID requestId, UUID reviewerId) {
        AccountOpeningRequest request = requireUnderReview(requestId);
        Account account = accountService.open(request.getCustomerId(), request.getAccountType());
        request.approve(reviewerId, account.getId());
        AccountOpeningRequest saved = requestRepository.save(request);
        outboxWriter.write("account.approved", "account", account.getId(),
                new AccountApprovedPayload(requestId, account.getId(), request.getCustomerId(), request.getAccountType()));
        auditEventPublisher.record(AuditAction.ACCOUNT_CREATED, "account", account.getId(), reviewerId, null,
                "SUCCESS", "Account opening request " + requestId + " approved");
        return saved;
    }

    @Transactional
    public AccountOpeningRequest reject(UUID requestId, UUID reviewerId, String reason) {
        AccountOpeningRequest request = requireUnderReview(requestId);
        request.reject(reviewerId, reason);
        return requestRepository.save(request);
    }

    private AccountOpeningRequest requireUnderReview(UUID requestId) {
        AccountOpeningRequest request = getOrThrow(requestId);
        if (request.getStatus() != AccountOpeningStatus.UNDER_REVIEW) {
            throw new InvalidAccountRequestTransitionException(
                    "Only a submission UNDER_REVIEW can be approved or rejected (current status: " + request.getStatus() + ")");
        }
        return request;
    }

    @Transactional(readOnly = true)
    public AccountOpeningRequest getOrThrow(UUID requestId) {
        return requestRepository.findById(requestId).orElseThrow(AccountRequestNotFoundException::new);
    }

    @Transactional(readOnly = true)
    public List<AccountOpeningRequest> historyForCustomer(UUID customerId) {
        return requestRepository.findByCustomerIdOrderByRequestedAtDesc(customerId);
    }

    @Transactional(readOnly = true)
    public Page<AccountOpeningRequest> queue(AccountOpeningStatus status, Pageable pageable) {
        return status != null ? requestRepository.findByStatus(status, pageable) : requestRepository.findAll(pageable);
    }

    private record AccountCreatedPayload(UUID requestId, UUID customerId, AccountType accountType) {
    }

    private record AccountApprovedPayload(UUID requestId, UUID accountId, UUID customerId, AccountType accountType) {
    }
}
