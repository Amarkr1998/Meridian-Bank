package com.meridianbank.ledger.reconciliation;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * The outcome of comparing one internal ledger transaction against its (possibly still-absent)
 * synthetic external counterpart — see {@link ReconciliationService} and
 * docs/reconciliation/reconciliation-design.md. Never mutates {@code ledger_entries}; this table
 * is reconciliation's entire footprint. One row per {@code transactionId}, re-evaluated on each
 * run while still {@code PENDING} (see {@link ReconciliationService#run}).
 */
@Entity
@Table(name = "reconciliation_records")
public class ReconciliationRecord {

    @Id
    private UUID id;

    @Column(name = "transaction_id", nullable = false, unique = true)
    private UUID transactionId;

    @Column(name = "internal_amount", nullable = false)
    private BigDecimal internalAmount;

    @Column(name = "internal_currency", nullable = false, length = 3)
    private String internalCurrency;

    @Column(name = "external_transaction_id")
    private UUID externalTransactionId;

    @Column(name = "external_amount")
    private BigDecimal externalAmount;

    @Column(name = "external_currency", length = 3)
    private String externalCurrency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ReconciliationStatus status;

    @Column(name = "mismatch_reason", length = 500)
    private String mismatchReason;

    @Column(name = "investigated_by")
    private UUID investigatedBy;

    @Column(name = "investigated_at")
    private Instant investigatedAt;

    @Column(name = "resolved_by")
    private UUID resolvedBy;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Column(name = "resolution_notes", length = 1000)
    private String resolutionNotes;

    @Column(name = "run_id", nullable = false)
    private UUID runId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ReconciliationRecord() {
    }

    public ReconciliationRecord(UUID transactionId, BigDecimal internalAmount, String internalCurrency,
                                 UUID runId) {
        this.id = UUID.randomUUID();
        this.transactionId = transactionId;
        this.internalAmount = internalAmount;
        this.internalCurrency = internalCurrency;
        this.status = ReconciliationStatus.PENDING;
        this.runId = runId;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public void markPending(UUID runId) {
        this.status = ReconciliationStatus.PENDING;
        this.runId = runId;
        this.updatedAt = Instant.now();
    }

    public void markMatched(ExternalTransaction external, UUID runId) {
        this.status = ReconciliationStatus.MATCHED;
        this.externalTransactionId = external.getId();
        this.externalAmount = external.getAmount();
        this.externalCurrency = external.getCurrency();
        this.mismatchReason = null;
        this.runId = runId;
        this.updatedAt = Instant.now();
    }

    public void markMismatched(ExternalTransaction external, String reason, UUID runId) {
        this.status = ReconciliationStatus.MISMATCHED;
        this.externalTransactionId = external.getId();
        this.externalAmount = external.getAmount();
        this.externalCurrency = external.getCurrency();
        this.mismatchReason = reason;
        this.runId = runId;
        this.updatedAt = Instant.now();
    }

    public void startInvestigation(UUID actorId) {
        this.status = ReconciliationStatus.INVESTIGATION;
        this.investigatedBy = actorId;
        this.investigatedAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public void resolve(UUID actorId, String notes) {
        this.status = ReconciliationStatus.RESOLVED;
        this.resolvedBy = actorId;
        this.resolvedAt = Instant.now();
        this.resolutionNotes = notes;
        this.updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getTransactionId() {
        return transactionId;
    }

    public BigDecimal getInternalAmount() {
        return internalAmount;
    }

    public String getInternalCurrency() {
        return internalCurrency;
    }

    public UUID getExternalTransactionId() {
        return externalTransactionId;
    }

    public BigDecimal getExternalAmount() {
        return externalAmount;
    }

    public String getExternalCurrency() {
        return externalCurrency;
    }

    public ReconciliationStatus getStatus() {
        return status;
    }

    public String getMismatchReason() {
        return mismatchReason;
    }

    public UUID getInvestigatedBy() {
        return investigatedBy;
    }

    public Instant getInvestigatedAt() {
        return investigatedAt;
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

    public UUID getRunId() {
        return runId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
