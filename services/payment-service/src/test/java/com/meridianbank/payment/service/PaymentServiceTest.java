package com.meridianbank.payment.service;

import com.meridianbank.payment.client.AccountServiceClient;
import com.meridianbank.payment.client.CustomerKycServiceClient;
import com.meridianbank.payment.client.FraudRiskServiceClient;
import com.meridianbank.payment.client.InsufficientBalanceException;
import com.meridianbank.payment.client.LedgerServiceClient;
import com.meridianbank.payment.domain.Transaction;
import com.meridianbank.payment.domain.TransactionStatus;
import com.meridianbank.payment.exception.TransactionNotFoundException;
import com.meridianbank.payment.outbox.OutboxWriter;
import com.meridianbank.payment.repository.TransactionRepository;
import com.meridianbank.payment.repository.TransactionStatusHistoryRepository;
import com.meridianbank.payment.web.dto.CreatePaymentRequest;
import com.meridianbank.payment.web.dto.PaymentResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock private TransactionRepository transactionRepository;
    @Mock private TransactionStatusHistoryRepository statusHistoryRepository;
    @Mock private IdempotencyService idempotencyService;
    @Mock private AccountServiceClient accountServiceClient;
    @Mock private CustomerKycServiceClient customerKycServiceClient;
    @Mock private LedgerServiceClient ledgerServiceClient;
    @Mock private FraudRiskServiceClient fraudRiskServiceClient;
    @Mock private OutboxWriter outboxWriter;
    @Mock private com.meridianbank.payment.audit.AuditEventPublisher auditEventPublisher;

    private PaymentService paymentService;

    private final UUID customerId = UUID.randomUUID();
    private final UUID sourceAccountId = UUID.randomUUID();
    private final UUID beneficiaryId = UUID.randomUUID();
    private final UUID destinationAccountId = UUID.randomUUID();
    private static final String TOKEN = "bearer-token";

    @BeforeEach
    void setUp() {
        paymentService = new PaymentService(transactionRepository, statusHistoryRepository, idempotencyService,
                accountServiceClient, customerKycServiceClient, ledgerServiceClient, fraudRiskServiceClient,
                outboxWriter, auditEventPublisher);
        lenient().when(transactionRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(transactionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(idempotencyService.claim(any(), anyString(), anyString()))
                .thenReturn(new IdempotencyService.ClaimResult(IdempotencyService.Outcome.CLAIMED, null));
        lenient().when(fraudRiskServiceClient.assess(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new FraudRiskServiceClient.RiskAssessmentResult(null, 0, "ALLOW", ""));
    }

    private CreatePaymentRequest request(BigDecimal amount) {
        return new CreatePaymentRequest(sourceAccountId, beneficiaryId, amount, "USD", "test payment");
    }

    private AccountServiceClient.AccountSummary activeSource() {
        return new AccountServiceClient.AccountSummary(sourceAccountId, "ACTIVE", "USD",
                new BigDecimal("5000.00"), new BigDecimal("20000.00"));
    }

    private AccountServiceClient.BeneficiarySummary activeBeneficiary() {
        return new AccountServiceClient.BeneficiarySummary(beneficiaryId, "ACTIVE", destinationAccountId, "ACTIVE");
    }

    @Test
    void createPayment_replayOutcome_returnsExistingTransactionWithoutCallingDownstreamServices() {
        UUID existingId = UUID.randomUUID();
        Transaction existing = new Transaction(customerId, sourceAccountId, beneficiaryId, BigDecimal.TEN, "USD",
                null, "key-1", "fingerprint");
        existing.succeed();
        when(idempotencyService.claim(eq(customerId), eq("key-1"), anyString()))
                .thenReturn(new IdempotencyService.ClaimResult(IdempotencyService.Outcome.REPLAY, existingId));
        when(transactionRepository.findById(existingId)).thenReturn(Optional.of(existing));

        PaymentResponse response = paymentService.createPayment(customerId, request(BigDecimal.TEN), "key-1", TOKEN);

        assertThat(response.id()).isEqualTo(existing.getId());
        assertThat(response.status()).isEqualTo(TransactionStatus.SUCCESS);
        verifyNoInteractions(accountServiceClient, customerKycServiceClient);
    }

    @Test
    void createPayment_replayOutcomeWithMissingTransaction_throws() {
        UUID missingId = UUID.randomUUID();
        when(idempotencyService.claim(any(), anyString(), anyString()))
                .thenReturn(new IdempotencyService.ClaimResult(IdempotencyService.Outcome.REPLAY, missingId));
        when(transactionRepository.findById(missingId)).thenReturn(Optional.empty());

        assertThrows(TransactionNotFoundException.class,
                () -> paymentService.createPayment(customerId, request(BigDecimal.TEN), "key-1", TOKEN));
    }

    @Test
    void createPayment_withInvalidSourceAccount_failsWithSourceAccountInvalid() {
        when(accountServiceClient.getAccount(sourceAccountId, TOKEN)).thenReturn(Optional.empty());

        PaymentResponse response = paymentService.createPayment(customerId, request(BigDecimal.TEN), "key-1", TOKEN);

        assertThat(response.status()).isEqualTo(TransactionStatus.FAILED);
        assertThat(response.failureCode()).isEqualTo("SOURCE_ACCOUNT_INVALID");
        verifyNoInteractions(customerKycServiceClient);
    }

    @Test
    void createPayment_withFrozenSourceAccount_failsWithSourceAccountNotActive() {
        AccountServiceClient.AccountSummary frozen = new AccountServiceClient.AccountSummary(
                sourceAccountId, "FROZEN", "USD", new BigDecimal("5000.00"), new BigDecimal("20000.00"));
        when(accountServiceClient.getAccount(sourceAccountId, TOKEN)).thenReturn(Optional.of(frozen));

        PaymentResponse response = paymentService.createPayment(customerId, request(BigDecimal.TEN), "key-1", TOKEN);

        assertThat(response.status()).isEqualTo(TransactionStatus.FAILED);
        assertThat(response.failureCode()).isEqualTo("SOURCE_ACCOUNT_NOT_ACTIVE");
    }

    @Test
    void createPayment_withUnverifiedBeneficiary_failsWithBeneficiaryNotActive() {
        when(accountServiceClient.getAccount(sourceAccountId, TOKEN)).thenReturn(Optional.of(activeSource()));
        AccountServiceClient.BeneficiarySummary pending = new AccountServiceClient.BeneficiarySummary(
                beneficiaryId, "PENDING", null, null);
        when(accountServiceClient.getBeneficiary(beneficiaryId, TOKEN)).thenReturn(Optional.of(pending));

        PaymentResponse response = paymentService.createPayment(customerId, request(BigDecimal.TEN), "key-1", TOKEN);

        assertThat(response.status()).isEqualTo(TransactionStatus.FAILED);
        assertThat(response.failureCode()).isEqualTo("BENEFICIARY_NOT_ACTIVE");
        verifyNoInteractions(customerKycServiceClient);
    }

    @Test
    void createPayment_withUnverifiedKyc_failsWithKycNotVerified() {
        when(accountServiceClient.getAccount(sourceAccountId, TOKEN)).thenReturn(Optional.of(activeSource()));
        when(accountServiceClient.getBeneficiary(beneficiaryId, TOKEN)).thenReturn(Optional.of(activeBeneficiary()));
        when(customerKycServiceClient.hasVerifiedKyc(customerId, TOKEN)).thenReturn(false);

        PaymentResponse response = paymentService.createPayment(customerId, request(BigDecimal.TEN), "key-1", TOKEN);

        assertThat(response.status()).isEqualTo(TransactionStatus.FAILED);
        assertThat(response.failureCode()).isEqualTo("KYC_NOT_VERIFIED");
    }

    @Test
    void createPayment_exceedingPerTransactionLimit_fails() {
        when(accountServiceClient.getAccount(sourceAccountId, TOKEN)).thenReturn(Optional.of(activeSource()));
        when(accountServiceClient.getBeneficiary(beneficiaryId, TOKEN)).thenReturn(Optional.of(activeBeneficiary()));
        when(customerKycServiceClient.hasVerifiedKyc(customerId, TOKEN)).thenReturn(true);

        PaymentResponse response = paymentService.createPayment(customerId, request(new BigDecimal("9999.00")), "key-1", TOKEN);

        assertThat(response.status()).isEqualTo(TransactionStatus.FAILED);
        assertThat(response.failureCode()).isEqualTo("PER_TRANSACTION_LIMIT_EXCEEDED");
    }

    @Test
    void createPayment_exceedingDailyLimit_fails() {
        when(accountServiceClient.getAccount(sourceAccountId, TOKEN)).thenReturn(Optional.of(activeSource()));
        when(accountServiceClient.getBeneficiary(beneficiaryId, TOKEN)).thenReturn(Optional.of(activeBeneficiary()));
        when(customerKycServiceClient.hasVerifiedKyc(customerId, TOKEN)).thenReturn(true);
        when(transactionRepository.sumSuccessfulAmountSince(eq(sourceAccountId), any()))
                .thenReturn(new BigDecimal("19995.00"));

        PaymentResponse response = paymentService.createPayment(customerId, request(new BigDecimal("10.00")), "key-1", TOKEN);

        assertThat(response.status()).isEqualTo(TransactionStatus.FAILED);
        assertThat(response.failureCode()).isEqualTo("DAILY_LIMIT_EXCEEDED");
    }

    @Test
    void createPayment_whenAllChecksPass_postsToLedgerAndSucceeds() {
        when(accountServiceClient.getAccount(sourceAccountId, TOKEN)).thenReturn(Optional.of(activeSource()));
        when(accountServiceClient.getBeneficiary(beneficiaryId, TOKEN)).thenReturn(Optional.of(activeBeneficiary()));
        when(customerKycServiceClient.hasVerifiedKyc(customerId, TOKEN)).thenReturn(true);
        when(transactionRepository.sumSuccessfulAmountSince(eq(sourceAccountId), any())).thenReturn(BigDecimal.ZERO);

        PaymentResponse response = paymentService.createPayment(customerId, request(new BigDecimal("100.00")), "key-1", TOKEN);

        assertThat(response.status()).isEqualTo(TransactionStatus.SUCCESS);
        assertThat(response.destinationAccountId()).isEqualTo(destinationAccountId);
        verify(ledgerServiceClient).post(eq(response.id()), eq(sourceAccountId), eq(destinationAccountId),
                eq(new BigDecimal("100.00")), eq("USD"), anyString(), eq(TOKEN));
        verify(idempotencyService).complete(eq(customerId), eq("key-1"), anyString(), any());
        verify(outboxWriter).write(eq("payment.initiated"), eq("transaction"), eq(response.id()), any());
        verify(outboxWriter).write(eq("payment.completed"), eq("transaction"), eq(response.id()), any());
    }

    @Test
    void createPayment_whenLedgerReportsInsufficientBalance_failsWithInsufficientBalance() {
        when(accountServiceClient.getAccount(sourceAccountId, TOKEN)).thenReturn(Optional.of(activeSource()));
        when(accountServiceClient.getBeneficiary(beneficiaryId, TOKEN)).thenReturn(Optional.of(activeBeneficiary()));
        when(customerKycServiceClient.hasVerifiedKyc(customerId, TOKEN)).thenReturn(true);
        when(transactionRepository.sumSuccessfulAmountSince(eq(sourceAccountId), any())).thenReturn(BigDecimal.ZERO);
        when(ledgerServiceClient.post(any(), any(), any(), any(), any(), any(), any()))
                .thenThrow(new InsufficientBalanceException());

        PaymentResponse response = paymentService.createPayment(customerId, request(new BigDecimal("100.00")), "key-1", TOKEN);

        assertThat(response.status()).isEqualTo(TransactionStatus.FAILED);
        assertThat(response.failureCode()).isEqualTo("INSUFFICIENT_BALANCE");
        verify(outboxWriter).write(eq("payment.failed"), eq("transaction"), eq(response.id()), any());
    }

    @Test
    void createPayment_whenFraudRiskServiceReturnsBlock_failsWithFraudBlocked() {
        when(accountServiceClient.getAccount(sourceAccountId, TOKEN)).thenReturn(Optional.of(activeSource()));
        when(accountServiceClient.getBeneficiary(beneficiaryId, TOKEN)).thenReturn(Optional.of(activeBeneficiary()));
        when(customerKycServiceClient.hasVerifiedKyc(customerId, TOKEN)).thenReturn(true);
        when(transactionRepository.sumSuccessfulAmountSince(eq(sourceAccountId), any())).thenReturn(BigDecimal.ZERO);
        when(fraudRiskServiceClient.assess(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new FraudRiskServiceClient.RiskAssessmentResult(null, 85, "BLOCK", "RAPID_SEQUENTIAL_TRANSFERS"));

        PaymentResponse response = paymentService.createPayment(customerId, request(new BigDecimal("100.00")), "key-1", TOKEN);

        assertThat(response.status()).isEqualTo(TransactionStatus.FAILED);
        assertThat(response.failureCode()).isEqualTo("FRAUD_BLOCKED");
        verifyNoInteractions(ledgerServiceClient);
    }

    @Test
    void createPayment_whenFraudRiskServiceReturnsReview_failsWithFraudReviewRequired() {
        when(accountServiceClient.getAccount(sourceAccountId, TOKEN)).thenReturn(Optional.of(activeSource()));
        when(accountServiceClient.getBeneficiary(beneficiaryId, TOKEN)).thenReturn(Optional.of(activeBeneficiary()));
        when(customerKycServiceClient.hasVerifiedKyc(customerId, TOKEN)).thenReturn(true);
        when(transactionRepository.sumSuccessfulAmountSince(eq(sourceAccountId), any())).thenReturn(BigDecimal.ZERO);
        when(fraudRiskServiceClient.assess(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new FraudRiskServiceClient.RiskAssessmentResult(null, 50, "REVIEW", "HIGH_AMOUNT"));

        PaymentResponse response = paymentService.createPayment(customerId, request(new BigDecimal("100.00")), "key-1", TOKEN);

        assertThat(response.status()).isEqualTo(TransactionStatus.FAILED);
        assertThat(response.failureCode()).isEqualTo("FRAUD_REVIEW_REQUIRED");
        verifyNoInteractions(ledgerServiceClient);
    }

    @Test
    void createPayment_currencyMismatch_fails() {
        when(accountServiceClient.getAccount(sourceAccountId, TOKEN)).thenReturn(Optional.of(activeSource()));
        when(accountServiceClient.getBeneficiary(beneficiaryId, TOKEN)).thenReturn(Optional.of(activeBeneficiary()));

        CreatePaymentRequest eurRequest = new CreatePaymentRequest(sourceAccountId, beneficiaryId,
                new BigDecimal("10.00"), "EUR", null);
        PaymentResponse response = paymentService.createPayment(customerId, eurRequest, "key-1", TOKEN);

        assertThat(response.status()).isEqualTo(TransactionStatus.FAILED);
        assertThat(response.failureCode()).isEqualTo("CURRENCY_MISMATCH");
        verifyNoInteractions(customerKycServiceClient);
    }
}
