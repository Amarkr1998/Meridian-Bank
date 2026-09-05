package com.meridianbank.fraud.approval;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

/**
 * A maker-checker request gating one sensitive action on one resource — see
 * docs/adr/0011-maker-checker.md. The maker never approves their own request: enforced
 * server-side in {@link ApprovalService#requirePending}, not just hidden in a UI (ADR-0011). Only
 * a single decision round is implemented — no "Return for revision" cycle (see
 * fraud-risk-service/README.md for that scope reduction).
 *
 * <p>{@code payload} carries the action-specific request details as a JSON string (the two gated
 * actions have genuinely different shapes — alert resolution needs a target status, a rule update
 * needs new weight/thresholds) — same envelope-as-JSON-string precedent already used by this
 * service's outbox ({@code OutboxWriter}).
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

    @Column(nullable = false, columnDefinition = "TEXT")
    private String payload;

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

    public ApprovalRequest(ApprovalActionType actionType, UUID resourceId, String payload, UUID requestedBy) {
        this.id = UUID.randomUUID();
        this.actionType = actionType;
        this.resourceId = resourceId;
        this.payload = payload;
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

    public String getPayload() {
        return payload;
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
