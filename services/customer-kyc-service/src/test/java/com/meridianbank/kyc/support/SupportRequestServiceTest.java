package com.meridianbank.kyc.support;

import com.meridianbank.kyc.audit.AuditAction;
import com.meridianbank.kyc.audit.AuditEventPublisher;
import com.meridianbank.kyc.outbox.OutboxWriter;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SupportRequestServiceTest {

    @Mock private SupportRequestRepository repository;
    @Mock private OutboxWriter outboxWriter;
    @Mock private AuditEventPublisher auditEventPublisher;

    private SupportRequestService service;

    @BeforeEach
    void setUp() {
        service = new SupportRequestService(repository, outboxWriter, auditEventPublisher);
        lenient().when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void create_savesAnOpenRequestAndAudits() {
        UUID customerId = UUID.randomUUID();

        SupportRequest result = service.create(customerId, SupportRequestCategory.PAYMENT, "Missing transfer",
                "My transfer never arrived");

        assertThat(result.getStatus()).isEqualTo(SupportRequestStatus.OPEN);
        assertThat(result.getCustomerId()).isEqualTo(customerId);
        verify(auditEventPublisher).record(eq(AuditAction.SUPPORT_REQUEST_CREATED), anyString(), any(),
                eq(customerId), anyString(), eq("OPEN"), anyString());
        verifyNoInteractions(outboxWriter);
    }

    @Test
    void startProgress_onAnOpenRequest_transitionsToInProgress() {
        SupportRequest request = new SupportRequest(UUID.randomUUID(), SupportRequestCategory.GENERAL, "s", "d");
        when(repository.findById(request.getId())).thenReturn(Optional.of(request));
        UUID staffId = UUID.randomUUID();

        SupportRequest result = service.startProgress(request.getId(), staffId);

        assertThat(result.getStatus()).isEqualTo(SupportRequestStatus.IN_PROGRESS);
        assertThat(result.getAssignedTo()).isEqualTo(staffId);
    }

    @Test
    void startProgress_onAnAlreadyInProgressRequest_throws() {
        SupportRequest request = new SupportRequest(UUID.randomUUID(), SupportRequestCategory.GENERAL, "s", "d");
        request.startProgress(UUID.randomUUID());
        when(repository.findById(request.getId())).thenReturn(Optional.of(request));

        assertThrows(InvalidSupportRequestTransitionException.class,
                () -> service.startProgress(request.getId(), UUID.randomUUID()));
    }

    @Test
    void resolve_onAnInProgressRequest_transitionsToResolvedAuditsAndNotifies() {
        UUID customerId = UUID.randomUUID();
        SupportRequest request = new SupportRequest(customerId, SupportRequestCategory.KYC, "Doc rejected",
                "Why was my document rejected?");
        request.startProgress(UUID.randomUUID());
        when(repository.findById(request.getId())).thenReturn(Optional.of(request));
        UUID staffId = UUID.randomUUID();

        SupportRequest result = service.resolve(request.getId(), staffId, "Resubmission requested via email");

        assertThat(result.getStatus()).isEqualTo(SupportRequestStatus.RESOLVED);
        assertThat(result.getResolvedBy()).isEqualTo(staffId);
        assertThat(result.getResolutionNotes()).isEqualTo("Resubmission requested via email");
        verify(auditEventPublisher).record(eq(AuditAction.SUPPORT_REQUEST_RESOLVED), anyString(), eq(request.getId()),
                eq(staffId), any(), eq("RESOLVED"), anyString());
        verify(outboxWriter).write(eq("notification.requested"), eq("support_request"), eq(request.getId()), any());
    }

    @Test
    void resolve_onAnOpenRequestNotYetInProgress_throwsAndNeverNotifies() {
        SupportRequest request = new SupportRequest(UUID.randomUUID(), SupportRequestCategory.GENERAL, "s", "d");
        when(repository.findById(request.getId())).thenReturn(Optional.of(request));

        assertThrows(InvalidSupportRequestTransitionException.class,
                () -> service.resolve(request.getId(), UUID.randomUUID(), "notes"));
        verifyNoInteractions(outboxWriter, auditEventPublisher);
    }

    @Test
    void getOrThrow_unknownId_throwsNotFound() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.empty());

        assertThrows(SupportRequestNotFoundException.class, () -> service.getOrThrow(id));
    }
}
