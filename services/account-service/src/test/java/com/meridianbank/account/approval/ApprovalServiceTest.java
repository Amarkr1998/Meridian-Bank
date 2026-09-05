package com.meridianbank.account.approval;

import com.meridianbank.account.audit.AuditEventPublisher;
import com.meridianbank.account.domain.Account;
import com.meridianbank.account.domain.AccountType;
import com.meridianbank.account.service.AccountService;
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
import static org.mockito.Mockito.*;

/** Proves the core maker-checker invariant server-side: a maker can never decide their own
 *  request — see docs/adr/0011-maker-checker.md. */
@ExtendWith(MockitoExtension.class)
class ApprovalServiceTest {

    @Mock private ApprovalRequestRepository repository;
    @Mock private AccountService accountService;
    @Mock private AuditEventPublisher auditEventPublisher;

    private ApprovalService service;

    @BeforeEach
    void setUp() {
        service = new ApprovalService(repository, accountService, auditEventPublisher);
        lenient().when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void create_whenNoExistingPendingRequest_createsOne() {
        UUID accountId = UUID.randomUUID();
        UUID maker = UUID.randomUUID();
        when(repository.existsByResourceIdAndActionTypeAndStatus(accountId, ApprovalActionType.BLOCK_ACCOUNT,
                ApprovalStatus.PENDING_APPROVAL)).thenReturn(false);

        ApprovalRequest request = service.create(ApprovalActionType.BLOCK_ACCOUNT, accountId, "fraud", maker);

        assertThat(request.getStatus()).isEqualTo(ApprovalStatus.PENDING_APPROVAL);
        assertThat(request.getRequestedBy()).isEqualTo(maker);
        assertThat(request.getResourceId()).isEqualTo(accountId);
    }

    @Test
    void create_whenAPendingRequestAlreadyExists_throws() {
        UUID accountId = UUID.randomUUID();
        when(repository.existsByResourceIdAndActionTypeAndStatus(accountId, ApprovalActionType.BLOCK_ACCOUNT,
                ApprovalStatus.PENDING_APPROVAL)).thenReturn(true);

        assertThrows(DuplicatePendingApprovalException.class,
                () -> service.create(ApprovalActionType.BLOCK_ACCOUNT, accountId, "fraud", UUID.randomUUID()));
    }

    @Test
    void approve_byTheMakerThemselves_isRejected() {
        UUID maker = UUID.randomUUID();
        ApprovalRequest request = new ApprovalRequest(ApprovalActionType.BLOCK_ACCOUNT, UUID.randomUUID(),
                "fraud", maker);
        when(repository.findById(request.getId())).thenReturn(Optional.of(request));

        assertThrows(SelfApprovalNotAllowedException.class, () -> service.approve(request.getId(), maker, null));
        verifyNoInteractions(accountService);
    }

    @Test
    void approve_byADifferentChecker_executesTheGatedAction() {
        UUID accountId = UUID.randomUUID();
        UUID maker = UUID.randomUUID();
        UUID checker = UUID.randomUUID();
        ApprovalRequest request = new ApprovalRequest(ApprovalActionType.BLOCK_ACCOUNT, accountId, "fraud", maker);
        when(repository.findById(request.getId())).thenReturn(Optional.of(request));
        when(accountService.block(accountId, "fraud", maker)).thenReturn(
                new Account(UUID.randomUUID(), "1234567890", AccountType.SAVINGS, "USD", BigDecimal.TEN, BigDecimal.TEN));

        ApprovalRequest result = service.approve(request.getId(), checker, "confirmed");

        assertThat(result.getStatus()).isEqualTo(ApprovalStatus.APPROVED);
        assertThat(result.getDecidedBy()).isEqualTo(checker);
        assertThat(result.getDecisionNotes()).isEqualTo("confirmed");
        verify(accountService).block(accountId, "fraud", maker);
    }

    @Test
    void reject_byADifferentChecker_neverExecutesTheGatedAction() {
        UUID maker = UUID.randomUUID();
        UUID checker = UUID.randomUUID();
        ApprovalRequest request = new ApprovalRequest(ApprovalActionType.BLOCK_ACCOUNT, UUID.randomUUID(),
                "fraud", maker);
        when(repository.findById(request.getId())).thenReturn(Optional.of(request));

        ApprovalRequest result = service.reject(request.getId(), checker, "insufficient evidence");

        assertThat(result.getStatus()).isEqualTo(ApprovalStatus.REJECTED);
        verifyNoInteractions(accountService);
    }

    @Test
    void approve_onAnAlreadyDecidedRequest_throws() {
        UUID maker = UUID.randomUUID();
        UUID checker = UUID.randomUUID();
        ApprovalRequest request = new ApprovalRequest(ApprovalActionType.BLOCK_ACCOUNT, UUID.randomUUID(),
                "fraud", maker);
        request.approve(checker, "already done");
        when(repository.findById(request.getId())).thenReturn(Optional.of(request));

        assertThrows(InvalidApprovalTransitionException.class,
                () -> service.approve(request.getId(), UUID.randomUUID(), null));
    }
}
