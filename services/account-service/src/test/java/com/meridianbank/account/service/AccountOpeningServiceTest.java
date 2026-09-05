package com.meridianbank.account.service;

import com.meridianbank.account.audit.AuditEventPublisher;
import com.meridianbank.account.client.CustomerKycServiceClient;
import com.meridianbank.account.domain.Account;
import com.meridianbank.account.domain.AccountOpeningRequest;
import com.meridianbank.account.domain.AccountType;
import com.meridianbank.account.exception.AccountRequestAlreadyInProgressException;
import com.meridianbank.account.exception.CustomerKycNotVerifiedException;
import com.meridianbank.account.exception.InvalidAccountRequestTransitionException;
import com.meridianbank.account.outbox.OutboxWriter;
import com.meridianbank.account.repository.AccountOpeningRequestRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccountOpeningServiceTest {

    @Mock private AccountOpeningRequestRepository requestRepository;
    @Mock private CustomerKycServiceClient customerKycServiceClient;
    @Mock private AccountService accountService;
    @Mock private OutboxWriter outboxWriter;
    @Mock private AuditEventPublisher auditEventPublisher;

    private AccountOpeningService accountOpeningService;

    @BeforeEach
    void setUp() {
        accountOpeningService = new AccountOpeningService(requestRepository, customerKycServiceClient, accountService,
                outboxWriter, auditEventPublisher);
    }

    @Test
    void submit_withoutVerifiedKyc_throws() {
        UUID customerId = UUID.randomUUID();
        when(requestRepository.existsByCustomerIdAndStatusIn(eq(customerId), anyList())).thenReturn(false);
        when(customerKycServiceClient.hasVerifiedKyc(customerId, "token")).thenReturn(false);

        assertThrows(CustomerKycNotVerifiedException.class,
                () -> accountOpeningService.submit(customerId, AccountType.SAVINGS, "token"));
    }

    @Test
    void submit_withExistingInProgressRequest_throwsWithoutCallingKyc() {
        UUID customerId = UUID.randomUUID();
        when(requestRepository.existsByCustomerIdAndStatusIn(eq(customerId), anyList())).thenReturn(true);

        assertThrows(AccountRequestAlreadyInProgressException.class,
                () -> accountOpeningService.submit(customerId, AccountType.SAVINGS, "token"));
    }

    @Test
    void submit_whenEligible_createsRequestedRecord() {
        UUID customerId = UUID.randomUUID();
        when(requestRepository.existsByCustomerIdAndStatusIn(eq(customerId), anyList())).thenReturn(false);
        when(customerKycServiceClient.hasVerifiedKyc(customerId, "token")).thenReturn(true);
        when(requestRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        AccountOpeningRequest request = accountOpeningService.submit(customerId, AccountType.CURRENT, "token");

        assertThat(request.getCustomerId()).isEqualTo(customerId);
        assertThat(request.getAccountType()).isEqualTo(AccountType.CURRENT);
        org.mockito.Mockito.verify(outboxWriter).write(eq("account.created"), eq("account_opening_request"), any(), any());
    }

    @Test
    void approve_onNonUnderReviewRequest_throws() {
        UUID requestId = UUID.randomUUID();
        AccountOpeningRequest request = new AccountOpeningRequest(UUID.randomUUID(), AccountType.SAVINGS); // ACCOUNT_REQUESTED
        when(requestRepository.findById(requestId)).thenReturn(Optional.of(request));

        assertThrows(InvalidAccountRequestTransitionException.class,
                () -> accountOpeningService.approve(requestId, UUID.randomUUID()));
    }

    @Test
    void approve_onUnderReviewRequest_createsAccountAndApproves() {
        UUID requestId = UUID.randomUUID();
        UUID reviewer = UUID.randomUUID();
        AccountOpeningRequest request = new AccountOpeningRequest(UUID.randomUUID(), AccountType.SAVINGS);
        request.startReview(reviewer);
        when(requestRepository.findById(requestId)).thenReturn(Optional.of(request));
        when(requestRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        Account createdAccount = new Account(request.getCustomerId(), "1112223334", AccountType.SAVINGS,
                "USD", java.math.BigDecimal.TEN, java.math.BigDecimal.TEN);
        when(accountService.open(request.getCustomerId(), AccountType.SAVINGS)).thenReturn(createdAccount);

        AccountOpeningRequest approved = accountOpeningService.approve(requestId, reviewer);

        assertThat(approved.getStatus().name()).isEqualTo("APPROVED");
        assertThat(approved.getAccountId()).isEqualTo(createdAccount.getId());
        org.mockito.Mockito.verify(outboxWriter).write(eq("account.approved"), eq("account"), any(), any());
    }

    @Test
    void reject_onUnderReviewRequest_recordsReason() {
        UUID requestId = UUID.randomUUID();
        UUID reviewer = UUID.randomUUID();
        AccountOpeningRequest request = new AccountOpeningRequest(UUID.randomUUID(), AccountType.SAVINGS);
        request.startReview(reviewer);
        when(requestRepository.findById(requestId)).thenReturn(Optional.of(request));
        when(requestRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        AccountOpeningRequest rejected = accountOpeningService.reject(requestId, reviewer, "insufficient information");

        assertThat(rejected.getStatus().name()).isEqualTo("REJECTED");
        assertThat(rejected.getRejectionReason()).isEqualTo("insufficient information");
    }
}
