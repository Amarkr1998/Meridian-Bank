package com.meridianbank.kyc.approval;

import com.meridianbank.kyc.domain.CustomerStatus;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

/**
 * A maker-checker request gating one sensitive action on one resource — see
 * docs/adr/0011-maker-checker.md. The maker never approves their own request: enforced
 * server-side in {@link ApprovalService#decide}, not just hidden in a UI (ADR-0011). Only a
 * single decision round is implemented — no "Return for revision" cycle (see
 * customer-kyc-service/README.md for that scope reduction).
 */
@Entity
@Table(name = "approval_requests")
public class ApprovalRequest {

    @Id
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "action_type", nullable = false, length = 30)
    private ApprovalActionType actionType;

    @Column(name = "resource_id", nullable = false)
    private UUID resourceId;

    /** Only populated for CUSTOMER_STATUS_CHANGE — the status the maker is requesting. */
    @Enumerated(EnumType.STRING)
    @Column(name = "requested_status", length = 20)
    private CustomerStatus requestedStatus;

    @Column(length = 500)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ApprovalStatus status;

    @Column(name = "requested_by", nullable = false)
    private UUID requestedBy;

    @Column(name = "requested_at", nullable = false)
    private Instant requestedAt;

    @Column(name = "decided_by")
    private UUID decidedBy;

    @Column(name = "decided_at")
    private Instant decidedAt;

    @Column(name = "decision_notes", length = 1000)
    private String decisionNotes;

    protected ApprovalRequest() {
    }

    public ApprovalRequest(ApprovalActionType actionType, UUID resourceId, CustomerStatus requestedStatus,
                            String reason, UUID requestedBy) {
        this.id = UUID.randomUUID();
        this.actionType = actionType;
        this.resourceId = resourceId;
        this.requestedStatus = requestedStatus;
        this.reason = reason;
        this.status = ApprovalStatus.PENDING_APPROVAL;
        this.requestedBy = requestedBy;
        this.requestedAt = Instant.now();
    }

    public void approve(UUID checkerId, String notes) {
        this.status = ApprovalStatus.APPROVED;
        this.decidedBy = checkerId;
        this.decidedAt = Instant.now();
        this.decisionNotes = notes;
    }

    public void reject(UUID checkerId, String notes) {
        this.status = ApprovalStatus.REJECTED;
        this.decidedBy = checkerId;
        this.decidedAt = Instant.now();
        this.decisionNotes = notes;
    }

    public UUID getId() {
        return id;
    }

    public ApprovalActionType getActionType() {
        return actionType;
    }

    public UUID getResourceId() {
        return resourceId;
    }

    public CustomerStatus getRequestedStatus() {
        return requestedStatus;
    }

    public String getReason() {
        return reason;
    }

    public ApprovalStatus getStatus() {
        return status;
    }

    public UUID getRequestedBy() {
        return requestedBy;
    }

    public Instant getRequestedAt() {
        return requestedAt;
    }

    public UUID getDecidedBy() {
        return decidedBy;
    }

    public Instant getDecidedAt() {
        return decidedAt;
    }

    public String getDecisionNotes() {
        return decisionNotes;
    }
}
