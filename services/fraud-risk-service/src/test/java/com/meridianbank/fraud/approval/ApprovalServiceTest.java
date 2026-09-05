package com.meridianbank.fraud.approval;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.meridianbank.fraud.audit.AuditEventPublisher;
import com.meridianbank.fraud.domain.FraudAlertStatus;
import com.meridianbank.fraud.service.FraudAlertService;
import com.meridianbank.fraud.service.FraudRuleService;
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
    @Mock private FraudAlertService fraudAlertService;
    @Mock private FraudRuleService fraudRuleService;
    @Mock private AuditEventPublisher auditEventPublisher;

    private ApprovalService service;

    @BeforeEach
    void setUp() {
        service = new ApprovalService(repository, fraudAlertService, fraudRuleService, new ObjectMapper(),
                auditEventPublisher);
        lenient().when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void requestAlertResolution_whenNoExistingPendingRequest_createsOne() {
        UUID alertId = UUID.randomUUID();
        UUID maker = UUID.randomUUID();
        when(repository.existsByResourceIdAndActionTypeAndStatus(alertId, ApprovalActionType.FRAUD_ALERT_RESOLUTION,
                ApprovalStatus.PENDING_APPROVAL)).thenReturn(false);

        ApprovalRequest request = service.requestAlertResolution(alertId, FraudAlertStatus.CLEARED, "legit", maker);

        assertThat(request.getStatus()).isEqualTo(ApprovalStatus.PENDING_APPROVAL);
        assertThat(request.getActionType()).isEqualTo(ApprovalActionType.FRAUD_ALERT_RESOLUTION);
        assertThat(request.getPayload()).contains("CLEARED").contains("legit");
    }

    @Test
    void requestAlertResolution_whenAPendingRequestAlreadyExists_throws() {
        UUID alertId = UUID.randomUUID();
        when(repository.existsByResourceIdAndActionTypeAndStatus(alertId, ApprovalActionType.FRAUD_ALERT_RESOLUTION,
                ApprovalStatus.PENDING_APPROVAL)).thenReturn(true);

        assertThrows(DuplicatePendingApprovalException.class, () -> service.requestAlertResolution(alertId,
                FraudAlertStatus.CLEARED, "legit", UUID.randomUUID()));
    }

    @Test
    void approve_byTheMakerThemselves_isRejected() {
        UUID maker = UUID.randomUUID();
        ApprovalRequest request = new ApprovalRequest(ApprovalActionType.FRAUD_ALERT_RESOLUTION, UUID.randomUUID(),
                "{}", maker);
        when(repository.findById(request.getId())).thenReturn(Optional.of(request));

        assertThrows(SelfApprovalNotAllowedException.class, () -> service.approve(request.getId(), maker, null));
        verifyNoInteractions(fraudAlertService, fraudRuleService);
    }

    @Test
    void approve_alertResolution_byADifferentChecker_executesTheClear() {
        UUID alertId = UUID.randomUUID();
        UUID maker = UUID.randomUUID();
        UUID checker = UUID.randomUUID();
        ApprovalRequest request = service.requestAlertResolution(alertId, FraudAlertStatus.CLEARED, "legit", maker);
        when(repository.findById(request.getId())).thenReturn(Optional.of(request));

        ApprovalRequest result = service.approve(request.getId(), checker, "agreed");

        assertThat(result.getStatus()).isEqualTo(ApprovalStatus.APPROVED);
        verify(fraudAlertService).clear(alertId, maker, "legit");
        verifyNoInteractions(fraudRuleService);
    }

    @Test
    void approve_ruleUpdate_byADifferentChecker_executesTheUpdate() {
        UUID ruleId = UUID.randomUUID();
        UUID maker = UUID.randomUUID();
        UUID checker = UUID.randomUUID();
        ApprovalRequest request = service.requestRuleUpdate(ruleId, 50, new BigDecimal("6000.00"), 3600, null, true,
                maker);
        when(repository.findById(request.getId())).thenReturn(Optional.of(request));

        service.approve(request.getId(), checker, null);

        verify(fraudRuleService).update(ruleId, 50, new BigDecimal("6000.00"), 3600, null, true, maker);
        verifyNoInteractions(fraudAlertService);
    }

    @Test
    void reject_neverExecutesTheGatedAction() {
        UUID maker = UUID.randomUUID();
        UUID checker = UUID.randomUUID();
        ApprovalRequest request = new ApprovalRequest(ApprovalActionType.FRAUD_ALERT_RESOLUTION, UUID.randomUUID(),
                "{\"targetStatus\":\"CLEARED\",\"notes\":\"x\"}", maker);
        when(repository.findById(request.getId())).thenReturn(Optional.of(request));

        ApprovalRequest result = service.reject(request.getId(), checker, "insufficient evidence");

        assertThat(result.getStatus()).isEqualTo(ApprovalStatus.REJECTED);
        verifyNoInteractions(fraudAlertService, fraudRuleService);
    }
}
