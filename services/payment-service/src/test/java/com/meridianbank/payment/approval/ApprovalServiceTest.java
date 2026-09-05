package com.meridianbank.payment.approval;

import com.meridianbank.payment.audit.AuditEventPublisher;
import com.meridianbank.payment.domain.TransactionStatus;
import com.meridianbank.payment.service.PaymentService;
import com.meridianbank.payment.web.dto.PaymentResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/** Proves the core maker-checker invariant server-side: a maker can never decide their own
 *  request — see docs/adr/0011-maker-checker.md. */
@ExtendWith(MockitoExtension.class)
class ApprovalServiceTest {

    @Mock private ApprovalRequestRepository repository;
    @Mock private PaymentService paymentService;
    @Mock private AuditEventPublisher auditEventPublisher;

    private ApprovalService service;

    private static final String CHECKER_TOKEN = "checker-bearer-token";

    @BeforeEach
    void setUp() {
        service = new ApprovalService(repository, paymentService, auditEventPublisher);
        lenient().when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void create_whenNoExistingPendingRequest_createsOne() {
        UUID transactionId = UUID.randomUUID();
        when(repository.existsByResourceIdAndActionTypeAndStatus(transactionId, ApprovalActionType.RELEASE_PAYMENT,
                ApprovalStatus.PENDING_APPROVAL)).thenReturn(false);

        ApprovalRequest request = service.create(transactionId, "reviewed, looks legitimate", UUID.randomUUID());

        assertThat(request.getStatus()).isEqualTo(ApprovalStatus.PENDING_APPROVAL);
        assertThat(request.getActionType()).isEqualTo(ApprovalActionType.RELEASE_PAYMENT);
        assertThat(request.getResourceId()).isEqualTo(transactionId);
    }

    @Test
    void create_whenAPendingRequestAlreadyExists_throws() {
        UUID transactionId = UUID.randomUUID();
        when(repository.existsByResourceIdAndActionTypeAndStatus(transactionId, ApprovalActionType.RELEASE_PAYMENT,
                ApprovalStatus.PENDING_APPROVAL)).thenReturn(true);

        assertThrows(DuplicatePendingApprovalException.class,
                () -> service.create(transactionId, "reason", UUID.randomUUID()));
    }

    @Test
    void approve_byTheMakerThemselves_isRejected() {
        UUID maker = UUID.randomUUID();
        ApprovalRequest request = new ApprovalRequest(ApprovalActionType.RELEASE_PAYMENT, UUID.randomUUID(),
                "reason", maker);
        when(repository.findById(request.getId())).thenReturn(Optional.of(request));

        assertThrows(SelfApprovalNotAllowedException.class,
                () -> service.approve(request.getId(), maker, null, CHECKER_TOKEN));
        verifyNoInteractions(paymentService);
    }

    @Test
    void approve_byADifferentChecker_releasesTheHeldPaymentAndRecordsTheResultTransaction() {
        UUID originalTransactionId = UUID.randomUUID();
        UUID maker = UUID.randomUUID();
        UUID checker = UUID.randomUUID();
        UUID resultTransactionId = UUID.randomUUID();
        ApprovalRequest request = new ApprovalRequest(ApprovalActionType.RELEASE_PAYMENT, originalTransactionId,
                "reviewed", maker);
        when(repository.findById(request.getId())).thenReturn(Optional.of(request));
        PaymentResponse released = new PaymentResponse(resultTransactionId, UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), new BigDecimal("40.00"), "USD", "test", TransactionStatus.SUCCESS, null, null,
                Instant.now(), Instant.now());
        when(paymentService.releaseHeldPayment(eq(originalTransactionId), eq(CHECKER_TOKEN))).thenReturn(released);

        ApprovalRequest result = service.approve(request.getId(), checker, "agreed", CHECKER_TOKEN);

        assertThat(result.getStatus()).isEqualTo(ApprovalStatus.APPROVED);
        assertThat(result.getResultTransactionId()).isEqualTo(resultTransactionId);
        verify(paymentService).releaseHeldPayment(originalTransactionId, CHECKER_TOKEN);
    }

    @Test
    void reject_neverReleasesTheHeldPayment() {
        UUID maker = UUID.randomUUID();
        UUID checker = UUID.randomUUID();
        ApprovalRequest request = new ApprovalRequest(ApprovalActionType.RELEASE_PAYMENT, UUID.randomUUID(),
                "reason", maker);
        when(repository.findById(request.getId())).thenReturn(Optional.of(request));

        ApprovalRequest result = service.reject(request.getId(), checker, "insufficient evidence");

        assertThat(result.getStatus()).isEqualTo(ApprovalStatus.REJECTED);
        assertThat(result.getResultTransactionId()).isNull();
        verifyNoInteractions(paymentService);
    }

    @Test
    void approve_onAnAlreadyDecidedRequest_throws() {
        UUID maker = UUID.randomUUID();
        UUID checker = UUID.randomUUID();
        ApprovalRequest request = new ApprovalRequest(ApprovalActionType.RELEASE_PAYMENT, UUID.randomUUID(),
                "reason", maker);
        request.reject(checker, "already decided");
        when(repository.findById(request.getId())).thenReturn(Optional.of(request));

        assertThrows(InvalidApprovalTransitionException.class,
                () -> service.approve(request.getId(), UUID.randomUUID(), null, CHECKER_TOKEN));
        verifyNoInteractions(paymentService);
    }
}
