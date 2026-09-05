package com.meridianbank.payment.domain;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

/** No changedBy actor: this pipeline is fully automatic in Phase 6 (no staff involvement yet —
 *  that arrives with maker-checker on high-value payments in Phase 10). */
@Entity
@Table(name = "transaction_status_history")
public class TransactionStatusHistory {

    @Id
    private UUID id;

    @Column(name = "transaction_id", nullable = false)
    private UUID transactionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "old_status", nullable = false, length = 20)
    private TransactionStatus oldStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "new_status", nullable = false, length = 20)
    private TransactionStatus newStatus;

    @Column
    private String reason;

    @Column(name = "changed_at", nullable = false)
    private Instant changedAt;

    protected TransactionStatusHistory() {
    }

    public TransactionStatusHistory(UUID transactionId, TransactionStatus oldStatus, TransactionStatus newStatus,
                                     String reason) {
        this.id = UUID.randomUUID();
        this.transactionId = transactionId;
        this.oldStatus = oldStatus;
        this.newStatus = newStatus;
        this.reason = reason;
        this.changedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getTransactionId() {
        return transactionId;
    }

    public TransactionStatus getOldStatus() {
        return oldStatus;
    }

    public TransactionStatus getNewStatus() {
        return newStatus;
    }

    public String getReason() {
        return reason;
    }

    public Instant getChangedAt() {
        return changedAt;
    }
}
