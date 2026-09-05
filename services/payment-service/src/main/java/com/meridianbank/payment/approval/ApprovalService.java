package com.meridianbank.payment.approval;

import com.meridianbank.payment.audit.AuditEventPublisher;
import com.meridianbank.payment.service.PaymentService;
import com.meridianbank.payment.web.dto.PaymentResponse;

import static com.meridianbank.payment.audit.AuditAction.APPROVAL_COMPLETED;
import static com.meridianbank.payment.audit.AuditAction.APPROVAL_CREATED;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Maker-checker workflow for payment-service's one gated action (RELEASE_PAYMENT) — see
 * docs/adr/0011-maker-checker.md. A maker requests release of a REVIEW-held (FAILED,
 * FRAUD_REVIEW_REQUIRED) transaction instead of it happening immediately; a different checker
 * must approve it before {@link PaymentService#releaseHeldPayment} actually runs and genuinely
 * moves money. Reject leaves the original transaction exactly as it was.
 */
@Service
public class ApprovalService {

    private final ApprovalRequestRepository repository;
    private final PaymentService paymentService;
    private final AuditEventPublisher auditEventPublisher;

    public ApprovalService(ApprovalRequestRepository repository, PaymentService paymentService,
                            AuditEventPublisher auditEventPublisher) {
        this.repository = repository;
        this.paymentService = paymentService;
        this.auditEventPublisher = auditEventPublisher;
    }

    @Transactional
    public ApprovalRequest create(UUID transactionId, String reason, UUID requestedBy) {
        if (repository.existsByResourceIdAndActionTypeAndStatus(transactionId, ApprovalActionType.RELEASE_PAYMENT,
                ApprovalStatus.PENDING_APPROVAL)) {
            throw new DuplicatePendingApprovalException();
        }
        ApprovalRequest request = repository.save(new ApprovalRequest(ApprovalActionType.RELEASE_PAYMENT,
                transactionId, reason, requestedBy));
        auditEventPublisher.record(APPROVAL_CREATED, "approval_request", request.getId(), requestedBy, null,
                "PENDING_APPROVAL", "RELEASE_PAYMENT requested on " + transactionId);
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
    public ApprovalRequest approve(UUID id, UUID checkerId, String notes, String checkerBearerToken) {
        ApprovalRequest request = requirePending(id, checkerId);
        PaymentResponse released = paymentService.releaseHeldPayment(request.getResourceId(), checkerBearerToken);
        request.approve(checkerId, notes, released.id());
        ApprovalRequest saved = repository.save(request);
        auditEventPublisher.record(APPROVAL_COMPLETED, "approval_request", saved.getId(), checkerId, null,
                "APPROVED", notes);
        return saved;
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
}
