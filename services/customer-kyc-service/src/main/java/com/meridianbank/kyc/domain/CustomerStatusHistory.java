package com.meridianbank.kyc.domain;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "customer_status_history")
public class CustomerStatusHistory {

    @Id
    private UUID id;

    @Column(name = "customer_id", nullable = false)
    private UUID customerId;

    @Enumerated(EnumType.STRING)
    @Column(name = "old_status", nullable = false, length = 20)
    private CustomerStatus oldStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "new_status", nullable = false, length = 20)
    private CustomerStatus newStatus;

    @Column
    private String reason;

    @Column(name = "changed_by", nullable = false)
    private UUID changedBy;

    @Column(name = "changed_at", nullable = false)
    private Instant changedAt;

    protected CustomerStatusHistory() {
    }

    public CustomerStatusHistory(UUID customerId, CustomerStatus oldStatus, CustomerStatus newStatus,
                                  String reason, UUID changedBy) {
        this.id = UUID.randomUUID();
        this.customerId = customerId;
        this.oldStatus = oldStatus;
        this.newStatus = newStatus;
        this.reason = reason;
        this.changedBy = changedBy;
        this.changedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getCustomerId() {
        return customerId;
    }

    public CustomerStatus getOldStatus() {
        return oldStatus;
    }

    public CustomerStatus getNewStatus() {
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
