package com.meridianbank.payment.domain;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * A payment attempt. Every well-formed, authenticated request that claims an idempotency key
 * gets a row here, whatever the eventual outcome — a FAILED transaction is a complete, valid
 * result, not an HTTP error (see PaymentController). {@code destinationAccountId} is resolved
 * from the beneficiary at validation time and snapshotted here, since the beneficiary's target
 * could theoretically change after the fact.
 */
@Entity
@Table(name = "transactions")
public class Transaction {

    @Id
    private UUID id;

    @Column(name = "customer_id", nullable = false)
    private UUID customerId;

    @Column(name = "source_account_id", nullable = false)
    private UUID sourceAccountId;

    @Column(name = "beneficiary_id", nullable = false)
    private UUID beneficiaryId;

    @Column(name = "destination_account_id")
    private UUID destinationAccountId;

    @Column(nullable = false)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column
    private String purpose;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TransactionStatus status = TransactionStatus.INITIATED;

    @Column(name = "failure_code")
    private String failureCode;

    @Column(name = "failure_reason")
    private String failureReason;

    @Column(name = "idempotency_key", nullable = false)
    private String idempotencyKey;

    @Column(name = "request_fingerprint", nullable = false)
    private String requestFingerprint;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    protected Transaction() {
    }

    public Transaction(UUID customerId, UUID sourceAccountId, UUID beneficiaryId, BigDecimal amount,
                        String currency, String purpose, String idempotencyKey, String requestFingerprint) {
        this.id = UUID.randomUUID();
        this.customerId = customerId;
        this.sourceAccountId = sourceAccountId;
        this.beneficiaryId = beneficiaryId;
        this.amount = amount;
        this.currency = currency;
        this.purpose = purpose;
        this.status = TransactionStatus.INITIATED;
        this.idempotencyKey = idempotencyKey;
        this.requestFingerprint = requestFingerprint;
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }

    public void moveTo(TransactionStatus status) {
        this.status = status;
    }

    public void fail(String code, String reason) {
        this.status = TransactionStatus.FAILED;
        this.failureCode = code;
        this.failureReason = reason;
        this.completedAt = Instant.now();
    }

    public void succeed() {
        this.status = TransactionStatus.SUCCESS;
        this.completedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getCustomerId() {
        return customerId;
    }

    public UUID getSourceAccountId() {
        return sourceAccountId;
    }

    public UUID getBeneficiaryId() {
        return beneficiaryId;
    }

    public UUID getDestinationAccountId() {
        return destinationAccountId;
    }

    public void setDestinationAccountId(UUID destinationAccountId) {
        this.destinationAccountId = destinationAccountId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getCurrency() {
        return currency;
    }

    public String getPurpose() {
        return purpose;
    }

    public TransactionStatus getStatus() {
        return status;
    }

    public String getFailureCode() {
        return failureCode;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public String getRequestFingerprint() {
        return requestFingerprint;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }
}
