package com.meridianbank.payment.service;

import com.meridianbank.payment.audit.AuditAction;
import com.meridianbank.payment.audit.AuditEventPublisher;
import com.meridianbank.payment.client.AccountServiceClient;
import com.meridianbank.payment.client.CustomerKycServiceClient;
import com.meridianbank.payment.client.FraudRiskServiceClient;
import com.meridianbank.payment.client.InsufficientBalanceException;
import com.meridianbank.payment.client.LedgerServiceClient;
import com.meridianbank.payment.domain.Transaction;
import com.meridianbank.payment.domain.TransactionStatus;
import com.meridianbank.payment.domain.TransactionStatusHistory;
import com.meridianbank.payment.exception.InvalidReleaseRequestException;
import com.meridianbank.payment.exception.TransactionNotFoundException;
import com.meridianbank.payment.outbox.OutboxWriter;
import com.meridianbank.payment.repository.TransactionRepository;
import com.meridianbank.payment.repository.TransactionStatusHistoryRepository;
import com.meridianbank.payment.web.dto.CreatePaymentRequest;
import com.meridianbank.payment.web.dto.PaymentResponse;
import com.meridianbank.payment.web.dto.TransactionStatusHistoryResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Payment orchestration: idempotency claim → source account / beneficiary / KYC / limit
 * validation → real fraud risk check → real ledger posting → terminal SUCCESS/FAILED.
 *
 * <p>As of Phase 7, a SUCCESS result means real money genuinely moved — see
 * {@link LedgerServiceClient} and ledger-service's double-entry posting
 * (docs/adr/0007-double-entry-ledger.md). {@code INSUFFICIENT_BALANCE} is now a real, ledger-
 * enforced failure, not a gap. As of Phase 9, RISK_CHECK calls
 * {@link FraudRiskServiceClient} for a real ALLOW/REVIEW/BLOCK decision — see
 * docs/architecture/fraud-flow.md. A BLOCK fails with {@code FRAUD_BLOCKED}. A REVIEW also fails,
 * with {@code FRAUD_REVIEW_REQUIRED}: a real implementation would place the payment on hold
 * pending manual review rather than failing it outright, but that hold/resume workflow belongs to
 * Phase 10's maker-checker infrastructure, which doesn't exist yet — until then, REVIEW is treated
 * as a distinct, honestly-labeled failure rather than either silently allowing the payment or
 * inventing an ad hoc hold mechanism ahead of the phase that owns it. See payment-service/README.md.
 */
@Service
public class PaymentService {

    private final TransactionRepository transactionRepository;
    private final TransactionStatusHistoryRepository statusHistoryRepository;
    private final IdempotencyService idempotencyService;
    private final AccountServiceClient accountServiceClient;
    private final CustomerKycServiceClient customerKycServiceClient;
    private final LedgerServiceClient ledgerServiceClient;
    private final FraudRiskServiceClient fraudRiskServiceClient;
    private final OutboxWriter outboxWriter;
    private final AuditEventPublisher auditEventPublisher;

    public PaymentService(TransactionRepository transactionRepository,
                           TransactionStatusHistoryRepository statusHistoryRepository,
                           IdempotencyService idempotencyService,
                           AccountServiceClient accountServiceClient,
                           CustomerKycServiceClient customerKycServiceClient,
                           LedgerServiceClient ledgerServiceClient,
                           FraudRiskServiceClient fraudRiskServiceClient,
                           OutboxWriter outboxWriter, AuditEventPublisher auditEventPublisher) {
        this.transactionRepository = transactionRepository;
        this.statusHistoryRepository = statusHistoryRepository;
        this.idempotencyService = idempotencyService;
        this.accountServiceClient = accountServiceClient;
        this.customerKycServiceClient = customerKycServiceClient;
        this.ledgerServiceClient = ledgerServiceClient;
        this.fraudRiskServiceClient = fraudRiskServiceClient;
        this.outboxWriter = outboxWriter;
        this.auditEventPublisher = auditEventPublisher;
    }

    @Transactional
    public PaymentResponse createPayment(UUID customerId, CreatePaymentRequest request, String idempotencyKey,
                                          String bearerToken) {
        String fingerprint = RequestFingerprint.of(request.sourceAccountId(), request.beneficiaryId(),
                request.amount(), request.currency());

        IdempotencyService.ClaimResult claim = idempotencyService.claim(customerId, idempotencyKey, fingerprint);
        if (claim.outcome() == IdempotencyService.Outcome.REPLAY) {
            Transaction existing = transactionRepository.findById(claim.replayTransactionId())
                    .orElseThrow(TransactionNotFoundException::new);
            return PaymentResponse.from(existing);
        }

        Transaction transaction = new Transaction(customerId, request.sourceAccountId(), request.beneficiaryId(),
                request.amount(), request.currency().toUpperCase(), request.purpose(), idempotencyKey, fingerprint);
        // Flushed immediately (rather than left to end-of-transaction) so the unique constraint on
        // (customer_id, idempotency_key) — the durable fallback behind the Redis claim above — is
        // checked now. In the near-impossible case both the Redis claim AND this constraint are
        // raced, this simply fails the request rather than attempting same-transaction recovery
        // (Postgres aborts the whole transaction on a constraint violation, which would make that
        // recovery unreliable) — see payment-service/README.md.
        transactionRepository.saveAndFlush(transaction);
        outboxWriter.write("payment.initiated", "transaction", transaction.getId(),
                new PaymentInitiatedPayload(transaction.getId(), transaction.getCustomerId(),
                        transaction.getSourceAccountId(), transaction.getBeneficiaryId(), transaction.getAmount(),
                        transaction.getCurrency()));
        auditInitiated(transaction);

        process(transaction, bearerToken);

        idempotencyService.complete(customerId, idempotencyKey, fingerprint, transaction.getId());
        return PaymentResponse.from(transaction);
    }

    private void process(Transaction transaction, String bearerToken) {
        transition(transaction, TransactionStatus.VALIDATING, null);

        Optional<AccountServiceClient.AccountSummary> sourceOpt =
                accountServiceClient.getAccount(transaction.getSourceAccountId(), bearerToken);
        if (sourceOpt.isEmpty()) {
            fail(transaction, "SOURCE_ACCOUNT_INVALID", "Source account not found or not owned by this customer");
            return;
        }
        AccountServiceClient.AccountSummary source = sourceOpt.get();
        if (!"ACTIVE".equals(source.status())) {
            fail(transaction, "SOURCE_ACCOUNT_NOT_ACTIVE", "Source account is not active (status: " + source.status() + ")");
            return;
        }

        Optional<AccountServiceClient.BeneficiarySummary> beneficiaryOpt =
                accountServiceClient.getBeneficiary(transaction.getBeneficiaryId(), bearerToken);
        if (beneficiaryOpt.isEmpty()) {
            fail(transaction, "BENEFICIARY_INVALID", "Beneficiary not found or not owned by this customer");
            return;
        }
        AccountServiceClient.BeneficiarySummary beneficiary = beneficiaryOpt.get();
        if (!"ACTIVE".equals(beneficiary.status())) {
            fail(transaction, "BENEFICIARY_NOT_ACTIVE",
                    "Beneficiary is not active/verified (status: " + beneficiary.status() + ")");
            return;
        }
        if (beneficiary.destinationAccountId() == null || !"ACTIVE".equals(beneficiary.destinationAccountStatus())) {
            fail(transaction, "DESTINATION_ACCOUNT_NOT_ACTIVE", "The beneficiary's destination account is not active");
            return;
        }
        transaction.setDestinationAccountId(beneficiary.destinationAccountId());

        if (!transaction.getCurrency().equals(source.currency())) {
            fail(transaction, "CURRENCY_MISMATCH", "Payment currency does not match the source account's currency");
            return;
        }

        if (!customerKycServiceClient.hasVerifiedKyc(transaction.getCustomerId(), bearerToken)) {
            fail(transaction, "KYC_NOT_VERIFIED", "Customer does not have a verified KYC record");
            return;
        }

        if (transaction.getAmount().compareTo(source.perTransactionLimit()) > 0) {
            fail(transaction, "PER_TRANSACTION_LIMIT_EXCEEDED",
                    "Amount exceeds the source account's per-transaction limit");
            return;
        }

        Instant startOfDayUtc = LocalDate.now(ZoneOffset.UTC).atStartOfDay(ZoneOffset.UTC).toInstant();
        BigDecimal usedToday = transactionRepository.sumSuccessfulAmountSince(transaction.getSourceAccountId(), startOfDayUtc);
        if (usedToday.add(transaction.getAmount()).compareTo(source.dailyLimit()) > 0) {
            fail(transaction, "DAILY_LIMIT_EXCEEDED", "Amount would exceed the source account's daily limit");
            return;
        }

        FraudRiskServiceClient.RiskAssessmentResult risk = fraudRiskServiceClient.assess(transaction.getId(),
                transaction.getCustomerId(), transaction.getSourceAccountId(), transaction.getDestinationAccountId(),
                transaction.getAmount(), transaction.getCurrency(), bearerToken);
        transition(transaction, TransactionStatus.RISK_CHECK,
                risk.decision() + " — score " + risk.score() + " (rule hits: " + risk.ruleHits() + ")");
        if ("BLOCK".equals(risk.decision())) {
            fail(transaction, "FRAUD_BLOCKED",
                    "Blocked by fraud risk assessment (score: " + risk.score() + ")");
            return;
        }
        if ("REVIEW".equals(risk.decision())) {
            fail(transaction, "FRAUD_REVIEW_REQUIRED",
                    "Flagged for manual fraud review (score: " + risk.score() + ") — no hold/resume workflow "
                            + "exists yet (Phase 10 maker-checker)");
            return;
        }

        transition(transaction, TransactionStatus.PROCESSING, null);
        try {
            ledgerServiceClient.post(transaction.getId(), transaction.getSourceAccountId(),
                    transaction.getDestinationAccountId(), transaction.getAmount(), transaction.getCurrency(),
                    transaction.getPurpose(), bearerToken);
        } catch (InsufficientBalanceException e) {
            fail(transaction, "INSUFFICIENT_BALANCE", "Source account has insufficient available balance");
            return;
        }

        TransactionStatus previous = transaction.getStatus();
        transaction.succeed();
        transactionRepository.save(transaction);
        recordHistory(transaction, previous, TransactionStatus.SUCCESS, null);
        outboxWriter.write("payment.completed", "transaction", transaction.getId(),
                new PaymentCompletedPayload(transaction.getId(), transaction.getCustomerId(),
                        transaction.getSourceAccountId(), transaction.getDestinationAccountId(),
                        transaction.getAmount(), transaction.getCurrency()));
        auditCompleted(transaction);
    }

    /**
     * Creates and processes a brand-new transaction that re-attempts a REVIEW-held payment,
     * bypassing RISK_CHECK entirely — staff have already exercised judgment to override the flag
     * (see {@code com.meridianbank.payment.approval.ApprovalService}, docs/adr/0011-maker-checker.md,
     * "high-value transaction approval"). Deliberately does NOT resume the original transaction
     * object or its idempotency claim — it reads the original's already-validated source/
     * destination/amount and creates a fresh, independent transaction referencing it, which is
     * both simpler and safer than reopening a transaction whose idempotency-key window may have
     * long since closed. The original transaction is left exactly as it was
     * (FAILED/FRAUD_REVIEW_REQUIRED) — it is not mutated.
     */
    @Transactional
    public PaymentResponse releaseHeldPayment(UUID originalTransactionId, String bearerToken) {
        Transaction original = getOrThrow(originalTransactionId);
        if (original.getStatus() != TransactionStatus.FAILED
                || !"FRAUD_REVIEW_REQUIRED".equals(original.getFailureCode())) {
            throw new InvalidReleaseRequestException(
                    "Only a transaction FAILED with FRAUD_REVIEW_REQUIRED can be released (current status: "
                            + original.getStatus() + ", failure code: " + original.getFailureCode() + ")");
        }

        String releaseIdempotencyKey = "release-" + original.getId();
        String fingerprint = RequestFingerprint.of(original.getSourceAccountId(), original.getBeneficiaryId(),
                original.getAmount(), original.getCurrency());
        Transaction released = new Transaction(original.getCustomerId(), original.getSourceAccountId(),
                original.getBeneficiaryId(), original.getAmount(), original.getCurrency(), original.getPurpose(),
                releaseIdempotencyKey, fingerprint);
        released.setDestinationAccountId(original.getDestinationAccountId());
        transactionRepository.saveAndFlush(released);
        outboxWriter.write("payment.initiated", "transaction", released.getId(),
                new PaymentInitiatedPayload(released.getId(), released.getCustomerId(),
                        released.getSourceAccountId(), released.getBeneficiaryId(), released.getAmount(),
                        released.getCurrency()));
        auditInitiated(released);

        transition(released, TransactionStatus.RISK_CHECK,
                "skipped — staff-approved release of transaction " + originalTransactionId);
        transition(released, TransactionStatus.PROCESSING, null);
        try {
            ledgerServiceClient.post(released.getId(), released.getSourceAccountId(),
                    released.getDestinationAccountId(), released.getAmount(), released.getCurrency(),
                    released.getPurpose(), bearerToken);
        } catch (InsufficientBalanceException e) {
            fail(released, "INSUFFICIENT_BALANCE", "Source account has insufficient available balance");
            return PaymentResponse.from(released);
        }

        TransactionStatus previous = released.getStatus();
        released.succeed();
        transactionRepository.save(released);
        recordHistory(released, previous, TransactionStatus.SUCCESS, null);
        outboxWriter.write("payment.completed", "transaction", released.getId(),
                new PaymentCompletedPayload(released.getId(), released.getCustomerId(),
                        released.getSourceAccountId(), released.getDestinationAccountId(), released.getAmount(),
                        released.getCurrency()));
        auditCompleted(released);
        return PaymentResponse.from(released);
    }

    private void fail(Transaction transaction, String code, String reason) {
        TransactionStatus previous = transaction.getStatus();
        transaction.fail(code, reason);
        transactionRepository.save(transaction);
        recordHistory(transaction, previous, TransactionStatus.FAILED, reason);
        outboxWriter.write("payment.failed", "transaction", transaction.getId(),
                new PaymentFailedPayload(transaction.getId(), transaction.getCustomerId(), code, reason));
        auditEventPublisher.record(AuditAction.PAYMENT_FAILED, "transaction", transaction.getId(),
                transaction.getCustomerId(), "CUSTOMER", code, reason);
    }

    private void auditInitiated(Transaction transaction) {
        auditEventPublisher.record(AuditAction.PAYMENT_INITIATED, "transaction", transaction.getId(),
                transaction.getCustomerId(), "CUSTOMER", "INITIATED", null);
    }

    private void auditCompleted(Transaction transaction) {
        auditEventPublisher.record(AuditAction.PAYMENT_COMPLETED, "transaction", transaction.getId(),
                transaction.getCustomerId(), "CUSTOMER", "SUCCESS", null);
    }

    private void transition(Transaction transaction, TransactionStatus target, String reason) {
        TransactionStatus previous = transaction.getStatus();
        transaction.moveTo(target);
        transactionRepository.save(transaction);
        recordHistory(transaction, previous, target, reason);
    }

    private void recordHistory(Transaction transaction, TransactionStatus from, TransactionStatus to, String reason) {
        statusHistoryRepository.save(new TransactionStatusHistory(transaction.getId(), from, to, reason));
    }

    @Transactional(readOnly = true)
    public Transaction getOrThrow(UUID id) {
        return transactionRepository.findById(id).orElseThrow(TransactionNotFoundException::new);
    }

    @Transactional(readOnly = true)
    public Page<Transaction> list(UUID customerId, TransactionStatus status, Pageable pageable) {
        if (customerId != null && status != null) {
            return transactionRepository.findByCustomerIdAndStatus(customerId, status, pageable);
        }
        if (customerId != null) {
            return transactionRepository.findByCustomerId(customerId, pageable);
        }
        if (status != null) {
            return transactionRepository.findByStatus(status, pageable);
        }
        return transactionRepository.findAll(pageable);
    }

    @Transactional(readOnly = true)
    public List<TransactionStatusHistoryResponse> getStatusHistory(UUID transactionId) {
        return statusHistoryRepository.findByTransactionIdOrderByChangedAtAsc(transactionId).stream()
                .map(TransactionStatusHistoryResponse::from)
                .toList();
    }

    private record PaymentInitiatedPayload(UUID transactionId, UUID customerId, UUID sourceAccountId,
                                            UUID beneficiaryId, BigDecimal amount, String currency) {
    }

    private record PaymentCompletedPayload(UUID transactionId, UUID customerId, UUID sourceAccountId,
                                            UUID destinationAccountId, BigDecimal amount, String currency) {
    }

    private record PaymentFailedPayload(UUID transactionId, UUID customerId, String failureCode,
                                         String failureReason) {
    }
}
