package com.meridianbank.kyc.support;

import com.meridianbank.kyc.audit.AuditAction;
import com.meridianbank.kyc.audit.AuditEventPublisher;
import com.meridianbank.kyc.outbox.OutboxWriter;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Customer-raised support tickets — see docs/database/domain-model.md ("Exact ownership of
 * support_requests will be finalized in Phase 13") and V4__add_support_requests.sql's comment for
 * why this service owns it. Not maker-checker gated — resolving a support ticket isn't in
 * ADR-0011's list of governance-sensitive actions, so this is a normal single-actor staff action,
 * same as a KYC review decision.
 */
@Service
public class SupportRequestService {

    private final SupportRequestRepository repository;
    private final OutboxWriter outboxWriter;
    private final AuditEventPublisher auditEventPublisher;

    public SupportRequestService(SupportRequestRepository repository, OutboxWriter outboxWriter,
                                  AuditEventPublisher auditEventPublisher) {
        this.repository = repository;
        this.outboxWriter = outboxWriter;
        this.auditEventPublisher = auditEventPublisher;
    }

    @Transactional
    public SupportRequest create(UUID customerId, SupportRequestCategory category, String subject,
                                  String description) {
        SupportRequest request = repository.save(new SupportRequest(customerId, category, subject, description));
        auditEventPublisher.record(AuditAction.SUPPORT_REQUEST_CREATED, "support_request", request.getId(),
                customerId, "CUSTOMER", "OPEN", subject);
        return request;
    }

    @Transactional(readOnly = true)
    public SupportRequest getOrThrow(UUID id) {
        return repository.findById(id).orElseThrow(SupportRequestNotFoundException::new);
    }

    @Transactional(readOnly = true)
    public Page<SupportRequest> listForCustomer(UUID customerId, SupportRequestStatus status, Pageable pageable) {
        return status != null
                ? repository.findByCustomerIdAndStatus(customerId, status, pageable)
                : repository.findByCustomerId(customerId, pageable);
    }

    @Transactional(readOnly = true)
    public Page<SupportRequest> queue(SupportRequestStatus status, Pageable pageable) {
        return status != null ? repository.findByStatus(status, pageable) : repository.findAll(pageable);
    }

    @Transactional
    public SupportRequest startProgress(UUID id, UUID staffId) {
        SupportRequest request = getOrThrow(id);
        if (request.getStatus() != SupportRequestStatus.OPEN) {
            throw new InvalidSupportRequestTransitionException(
                    "Only an OPEN request can be claimed (current status: " + request.getStatus() + ")");
        }
        request.startProgress(staffId);
        return repository.save(request);
    }

    @Transactional
    public SupportRequest resolve(UUID id, UUID staffId, String notes) {
        SupportRequest request = getOrThrow(id);
        if (request.getStatus() != SupportRequestStatus.IN_PROGRESS) {
            throw new InvalidSupportRequestTransitionException(
                    "Only a request IN_PROGRESS can be resolved (current status: " + request.getStatus() + ")");
        }
        request.resolve(staffId, notes);
        SupportRequest saved = repository.save(request);
        auditEventPublisher.record(AuditAction.SUPPORT_REQUEST_RESOLVED, "support_request", saved.getId(), staffId,
                null, "RESOLVED", notes);
        // notification-service (Phase 13) consumes this generic topic — see docs/kafka/topics.md
        // ("notification.requested | any service | notification-service"). This is the first real
        // producer to it.
        outboxWriter.write("notification.requested", "support_request", saved.getId(),
                new NotificationRequestedPayload(saved.getCustomerId(), "SUPPORT_REQUEST_RESOLVED",
                        "Your support request has been resolved",
                        "Re: \"" + saved.getSubject() + "\" — " + notes));
        return saved;
    }

    private record NotificationRequestedPayload(UUID customerId, String type, String title, String body) {
    }
}
