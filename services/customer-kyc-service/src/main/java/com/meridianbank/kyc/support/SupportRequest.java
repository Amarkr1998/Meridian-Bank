package com.meridianbank.kyc.support;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

/**
 * A customer-raised support ticket — see docs/database/domain-model.md ("Exact ownership of
 * support_requests will be finalized in Phase 13"): owned here since a support request is
 * fundamentally tied to the customer raising it, not to any particular account or transaction.
 * OPEN -> IN_PROGRESS -> RESOLVED, terminal (see V4 migration's comment for the deliberate scope
 * reduction — no reopen cycle).
 */
@Entity
@Table(name = "support_requests")
public class SupportRequest {

    @Id
    private UUID id;

    @Column(name = "customer_id", nullable = false)
    private UUID customerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SupportRequestCategory category;

    @Column(nullable = false, length = 200)
    private String subject;

    @Column(nullable = false, length = 2000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SupportRequestStatus status;

    @Column(name = "assigned_to")
    private UUID assignedTo;

    @Column(name = "assigned_at")
    private Instant assignedAt;

    @Column(name = "resolved_by")
    private UUID resolvedBy;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Column(name = "resolution_notes", length = 1000)
    private String resolutionNotes;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected SupportRequest() {
    }

    public SupportRequest(UUID customerId, SupportRequestCategory category, String subject, String description) {
        this.id = UUID.randomUUID();
        this.customerId = customerId;
        this.category = category;
        this.subject = subject;
        this.description = description;
        this.status = SupportRequestStatus.OPEN;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public void startProgress(UUID staffId) {
        this.status = SupportRequestStatus.IN_PROGRESS;
        this.assignedTo = staffId;
        this.assignedAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public void resolve(UUID staffId, String notes) {
        this.status = SupportRequestStatus.RESOLVED;
        this.resolvedBy = staffId;
        this.resolvedAt = Instant.now();
        this.resolutionNotes = notes;
        this.updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getCustomerId() {
        return customerId;
    }

    public SupportRequestCategory getCategory() {
        return category;
    }

    public String getSubject() {
        return subject;
    }

    public String getDescription() {
        return description;
    }

    public SupportRequestStatus getStatus() {
        return status;
    }

    public UUID getAssignedTo() {
        return assignedTo;
    }

    public Instant getAssignedAt() {
        return assignedAt;
    }

    public UUID getResolvedBy() {
        return resolvedBy;
    }

    public Instant getResolvedAt() {
        return resolvedAt;
    }

    public String getResolutionNotes() {
        return resolutionNotes;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
