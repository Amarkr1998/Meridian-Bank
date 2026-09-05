package com.meridianbank.kyc.approval;

import com.meridianbank.kyc.audit.AuditEventPublisher;
import com.meridianbank.kyc.domain.CustomerStatus;
import com.meridianbank.kyc.service.CustomerService;
import com.meridianbank.kyc.web.dto.UpdateCustomerStatusRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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
    @Mock private CustomerService customerService;
    @Mock private AuditEventPublisher auditEventPublisher;

    private ApprovalService service;

    @BeforeEach
    void setUp() {
        service = new ApprovalService(repository, customerService, auditEventPublisher);
        lenient().when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void create_whenNoExistingPendingRequest_createsOne() {
        UUID customerId = UUID.randomUUID();
        UUID maker = UUID.randomUUID();
        when(repository.existsByResourceIdAndActionTypeAndStatus(customerId,
                ApprovalActionType.CUSTOMER_STATUS_CHANGE, ApprovalStatus.PENDING_APPROVAL)).thenReturn(false);

        ApprovalRequest request = service.create(ApprovalActionType.CUSTOMER_STATUS_CHANGE, customerId,
                CustomerStatus.BLOCKED, "suspected fraud", maker);

        assertThat(request.getStatus()).isEqualTo(ApprovalStatus.PENDING_APPROVAL);
        assertThat(request.getRequestedStatus()).isEqualTo(CustomerStatus.BLOCKED);
    }

    @Test
    void create_whenAPendingRequestAlreadyExists_throws() {
        UUID customerId = UUID.randomUUID();
        when(repository.existsByResourceIdAndActionTypeAndStatus(customerId,
                ApprovalActionType.CUSTOMER_STATUS_CHANGE, ApprovalStatus.PENDING_APPROVAL)).thenReturn(true);

        assertThrows(DuplicatePendingApprovalException.class,
                () -> service.create(ApprovalActionType.CUSTOMER_STATUS_CHANGE, customerId, CustomerStatus.BLOCKED,
                        "reason", UUID.randomUUID()));
    }

    @Test
    void approve_byTheMakerThemselves_isRejected() {
        UUID maker = UUID.randomUUID();
        ApprovalRequest request = new ApprovalRequest(ApprovalActionType.CUSTOMER_STATUS_CHANGE, UUID.randomUUID(),
                CustomerStatus.BLOCKED, "fraud", maker);
        when(repository.findById(request.getId())).thenReturn(Optional.of(request));

        assertThrows(SelfApprovalNotAllowedException.class, () -> service.approve(request.getId(), maker, null));
        verifyNoInteractions(customerService);
    }

    @Test
    void approve_byADifferentChecker_executesTheStatusChange() {
        UUID customerId = UUID.randomUUID();
        UUID maker = UUID.randomUUID();
        UUID checker = UUID.randomUUID();
        ApprovalRequest request = new ApprovalRequest(ApprovalActionType.CUSTOMER_STATUS_CHANGE, customerId,
                CustomerStatus.BLOCKED, "fraud", maker);
        when(repository.findById(request.getId())).thenReturn(Optional.of(request));

        ApprovalRequest result = service.approve(request.getId(), checker, "confirmed");

        assertThat(result.getStatus()).isEqualTo(ApprovalStatus.APPROVED);
        verify(customerService).updateStatus(eq(customerId),
                eq(new UpdateCustomerStatusRequest(CustomerStatus.BLOCKED, "fraud")), eq(maker));
    }

    @Test
    void reject_byADifferentChecker_neverExecutesTheStatusChange() {
        UUID maker = UUID.randomUUID();
        UUID checker = UUID.randomUUID();
        ApprovalRequest request = new ApprovalRequest(ApprovalActionType.CUSTOMER_STATUS_CHANGE, UUID.randomUUID(),
                CustomerStatus.BLOCKED, "fraud", maker);
        when(repository.findById(request.getId())).thenReturn(Optional.of(request));

        ApprovalRequest result = service.reject(request.getId(), checker, "insufficient evidence");

        assertThat(result.getStatus()).isEqualTo(ApprovalStatus.REJECTED);
        verifyNoInteractions(customerService);
    }
}
