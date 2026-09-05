package com.meridianbank.ledger.domain;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * A single side of a double-entry posting — see docs/adr/0007-double-entry-ledger.md. Every
 * successful transfer produces exactly two of these (one DEBIT, one CREDIT) sharing a
 * {@code transactionId}, created in the same database transaction as the other. Append-only:
 * never updated or deleted — a correction is a new, inverse pair of entries under a new
 * transaction id, not a mutation of this one.
 */
@Entity
@Table(name = "ledger_entries")
public class LedgerEntry {

    @Id
    @Column(name = "ledger_entry_id")
    private UUID ledgerEntryId;

    @Column(name = "transaction_id", nullable = false)
    private UUID transactionId;

    @Column(name = "account_id", nullable = false)
    private UUID accountId;

    @Enumerated(EnumType.STRING)
    @Column(name = "entry_type", nullable = false, length = 10)
    private EntryType entryType;

    @Column(nullable = false)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column
    private String reference;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected LedgerEntry() {
    }

    public LedgerEntry(UUID transactionId, UUID accountId, EntryType entryType, BigDecimal amount,
                        String currency, String reference) {
        this.ledgerEntryId = UUID.randomUUID();
        this.transactionId = transactionId;
        this.accountId = accountId;
        this.entryType = entryType;
        this.amount = amount;
        this.currency = currency;
        this.reference = reference;
        this.createdAt = Instant.now();
    }

    public UUID getLedgerEntryId() {
        return ledgerEntryId;
    }

    public UUID getTransactionId() {
        return transactionId;
    }

    public UUID getAccountId() {
        return accountId;
    }

    public EntryType getEntryType() {
        return entryType;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getCurrency() {
        return currency;
    }

    public String getReference() {
        return reference;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
