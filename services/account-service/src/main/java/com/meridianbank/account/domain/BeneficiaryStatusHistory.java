package com.meridianbank.account.domain;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "beneficiary_status_history")
public class BeneficiaryStatusHistory {

    @Id
    private UUID id;

    @Column(name = "beneficiary_id", nullable = false)
    private UUID beneficiaryId;

    @Enumerated(EnumType.STRING)
    @Column(name = "old_status", nullable = false, length = 20)
    private BeneficiaryStatus oldStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "new_status", nullable = false, length = 20)
    private BeneficiaryStatus newStatus;

    @Column
    private String reason;

    @Column(name = "changed_by", nullable = false)
    private UUID changedBy;

    @Column(name = "changed_at", nullable = false)
    private Instant changedAt;

    protected BeneficiaryStatusHistory() {
    }

    public BeneficiaryStatusHistory(UUID beneficiaryId, BeneficiaryStatus oldStatus, BeneficiaryStatus newStatus,
                                     String reason, UUID changedBy) {
        this.id = UUID.randomUUID();
        this.beneficiaryId = beneficiaryId;
        this.oldStatus = oldStatus;
        this.newStatus = newStatus;
        this.reason = reason;
        this.changedBy = changedBy;
        this.changedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getBeneficiaryId() {
        return beneficiaryId;
    }

    public BeneficiaryStatus getOldStatus() {
        return oldStatus;
    }

    public BeneficiaryStatus getNewStatus() {
        return newStatus;
    }

    public String getReason() {
        return reason;
    }

    public UUID getChangedBy() {
        return changedBy;
    }

    public Instant getChangedAt() {
        return changedAt;
    }
}
