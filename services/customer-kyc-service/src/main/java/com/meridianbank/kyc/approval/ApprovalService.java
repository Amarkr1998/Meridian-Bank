package com.meridianbank.kyc.approval;

import com.meridianbank.kyc.audit.AuditAction;
import com.meridianbank.kyc.audit.AuditEventPublisher;
import com.meridianbank.kyc.domain.CustomerStatus;
import com.meridianbank.kyc.service.CustomerService;
import com.meridianbank.kyc.web.dto.UpdateCustomerStatusRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Maker-checker workflow for customer-kyc-service's one gated action (CUSTOMER_STATUS_CHANGE) —
 * see docs/adr/0011-maker-checker.md. A maker creates a PENDING_APPROVAL request instead of the
 * status change executing immediately; a different checker must approve it before
 * {@link CustomerService#updateStatus} actually runs. Reject leaves the customer untouched.
 */
@Service
public class ApprovalService {

    private final ApprovalRequestRepository repository;
    private final CustomerService customerService;
    private final AuditEventPublisher auditEventPublisher;

    public ApprovalService(ApprovalRequestRepository repository, CustomerService customerService,
                            AuditEventPublisher auditEventPublisher) {
        this.repository = repository;
        this.customerService = customerService;
        this.auditEventPublisher = auditEventPublisher;
    }

    @Transactional
    public ApprovalRequest create(ApprovalActionType actionType, UUID resourceId, CustomerStatus requestedStatus,
                                   String reason, UUID requestedBy) {
        if (repository.existsByResourceIdAndActionTypeAndStatus(resourceId, actionType,
                ApprovalStatus.PENDING_APPROVAL)) {
            throw new DuplicatePendingApprovalException();
        }
        ApprovalRequest request = repository.save(
                new ApprovalRequest(actionType, resourceId, requestedStatus, reason, requestedBy));
        auditEventPublisher.record(AuditAction.APPROVAL_CREATED, "approval_request", request.getId(), requestedBy,
                null, "PENDING_APPROVAL", actionType + " requested on " + resourceId);
        return request;
    }

    @Transactional(readOnly = true)
    public ApprovalRequest getOrThrow(UUID id) {
        return repository.findById(id).orElseThrow(ApprovalRequestNotFoundException::new);
    }

    @Transactional(readOnly = true)
    public Page<ApprovalRequest> queue(ApprovalStatus status, Pageable pageable) {
        return status != null ? repository.findByStatus(status, pageable) : repository.findAll(pageable);
    }

    @Transactional
    public ApprovalRequest approve(UUID id, UUID checkerId, String notes) {
        ApprovalRequest request = requirePending(id, checkerId);
        request.approve(checkerId, notes);
        repository.save(request);
        execute(request);
        auditEventPublisher.record(AuditAction.APPROVAL_COMPLETED, "approval_request", request.getId(), checkerId,
                null, "APPROVED", notes);
        return request;
    }

    @Transactional
    public ApprovalRequest reject(UUID id, UUID checkerId, String notes) {
        ApprovalRequest request = requirePending(id, checkerId);
        request.reject(checkerId, notes);
        ApprovalRequest saved = repository.save(request);
        auditEventPublisher.record(AuditAction.APPROVAL_COMPLETED, "approval_request", saved.getId(), checkerId,
                null, "REJECTED", notes);
        return saved;
    }

    private ApprovalRequest requirePending(UUID id, UUID checkerId) {
        ApprovalRequest request = getOrThrow(id);
        if (request.getStatus() != ApprovalStatus.PENDING_APPROVAL) {
            throw new InvalidApprovalTransitionException(
                    "Only a PENDING_APPROVAL request can be decided (current status: " + request.getStatus() + ")");
        }
        if (request.getRequestedBy().equals(checkerId)) {
            throw new SelfApprovalNotAllowedException();
        }
        return request;
    }

    private void execute(ApprovalRequest request) {
        switch (request.getActionType()) {
            case CUSTOMER_STATUS_CHANGE -> customerService.updateStatus(request.getResourceId(),
                    new UpdateCustomerStatusRequest(request.getRequestedStatus(), request.getReason()),
                    request.getRequestedBy());
        }
    }
}
