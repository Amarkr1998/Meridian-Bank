package com.meridianbank.account.approval;

import com.meridianbank.account.audit.AuditEventPublisher;
import com.meridianbank.account.service.AccountService;

import static com.meridianbank.account.audit.AuditAction.APPROVAL_COMPLETED;
import static com.meridianbank.account.audit.AuditAction.APPROVAL_CREATED;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Maker-checker workflow for account-service's one gated action (BLOCK_ACCOUNT) — see
 * docs/adr/0011-maker-checker.md. A maker creates a PENDING_APPROVAL request instead of the
 * action executing immediately; a different checker must approve it before
 * {@link AccountService#block} actually runs. Reject leaves the account untouched.
 */
@Service
public class ApprovalService {

    private final ApprovalRequestRepository repository;
    private final AccountService accountService;
    private final AuditEventPublisher auditEventPublisher;

    public ApprovalService(ApprovalRequestRepository repository, AccountService accountService,
                            AuditEventPublisher auditEventPublisher) {
        this.repository = repository;
        this.accountService = accountService;
        this.auditEventPublisher = auditEventPublisher;
    }

    @Transactional
    public ApprovalRequest create(ApprovalActionType actionType, UUID resourceId, String reason, UUID requestedBy) {
        if (repository.existsByResourceIdAndActionTypeAndStatus(resourceId, actionType,
                ApprovalStatus.PENDING_APPROVAL)) {
            throw new DuplicatePendingApprovalException();
        }
        ApprovalRequest request = repository.save(new ApprovalRequest(actionType, resourceId, reason, requestedBy));
        auditEventPublisher.record(APPROVAL_CREATED, "approval_request", request.getId(), requestedBy, null,
                "PENDING_APPROVAL", actionType + " requested on " + resourceId);
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
        auditEventPublisher.record(APPROVAL_COMPLETED, "approval_request", request.getId(), checkerId, null,
                "APPROVED", notes);
        return request;
    }

    @Transactional
    public ApprovalRequest reject(UUID id, UUID checkerId, String notes) {
        ApprovalRequest request = requirePending(id, checkerId);
        request.reject(checkerId, notes);
        ApprovalRequest saved = repository.save(request);
        auditEventPublisher.record(APPROVAL_COMPLETED, "approval_request", saved.getId(), checkerId, null,
                "REJECTED", notes);
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
            case BLOCK_ACCOUNT -> accountService.block(request.getResourceId(), request.getReason(),
                    request.getRequestedBy());
        }
    }
}
