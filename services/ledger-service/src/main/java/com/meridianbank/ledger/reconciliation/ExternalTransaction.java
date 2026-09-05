package com.meridianbank.ledger.reconciliation;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * A synthetic, locally-generated stand-in for an external system's own record of a transaction —
 * never real external bank data or a live third-party feed. See {@link ExternalFeedGenerator}'s
 * Javadoc for exactly how {@code amount} and {@code availableAt} are chosen, and
 * docs/reconciliation/reconciliation-design.md.
 */
@Entity
@Table(name = "external_transactions")
public class ExternalTransaction {

    @Id
    private UUID id;

    @Column(nullable = false, unique = true)
    private UUID reference;

    @Column(nullable = false)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(name = "external_status", nullable = false, length = 20)
    private String externalStatus;

    @Column(name = "generated_at", nullable = false)
    private Instant generatedAt;

    @Column(name = "available_at", nullable = false)
    private Instant availableAt;

    protected ExternalTransaction() {
    }

    public ExternalTransaction(UUID reference, BigDecimal amount, String currency, Instant availableAt) {
        this.id = UUID.randomUUID();
        this.reference = reference;
        this.amount = amount;
        this.currency = currency;
        this.externalStatus = "SETTLED";
        this.generatedAt = Instant.now();
        this.availableAt = availableAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getReference() {
        return reference;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getCurrency() {
        return currency;
    }

    public String getExternalStatus() {
        return externalStatus;
    }

    public Instant getGeneratedAt() {
        return generatedAt;
    }

    public Instant getAvailableAt() {
        return availableAt;
    }

    public boolean isAvailable(Instant now) {
        return !availableAt.isAfter(now);
    }
}
