package com.meridianbank.ledger.reconciliation;

import com.meridianbank.ledger.audit.AuditAction;
import com.meridianbank.ledger.audit.AuditEventPublisher;
import com.meridianbank.ledger.domain.EntryType;
import com.meridianbank.ledger.domain.LedgerEntry;
import com.meridianbank.ledger.outbox.OutboxWriter;
import com.meridianbank.ledger.repository.LedgerEntryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Compares every internal ledger transaction against its synthetic external counterpart (see
 * {@link ExternalFeedGenerator}) and classifies the result — see
 * docs/reconciliation/reconciliation-design.md. Never writes to {@code ledger_entries}; this
 * service's entire footprint is {@code reconciliation_records} (plus, transitively,
 * {@code external_transactions} via the generator). Invoked by {@link ReconciliationJob} on a
 * fixed schedule, but exposed as a plain method so tests can call {@link #run()} directly without
 * waiting on the scheduler.
 */
@Service
public class ReconciliationService {

    private static final Logger log = LoggerFactory.getLogger(ReconciliationService.class);

    /** Once a record leaves PENDING it is never touched by the job again — MISMATCHED,
     *  INVESTIGATION, and RESOLVED are all "owned by a human from here." Only PENDING is
     *  re-evaluated each run (waiting on the synthetic external record to become available). */
    private static final Set<ReconciliationStatus> TERMINAL_FOR_JOB =
            EnumSet.of(ReconciliationStatus.MATCHED, ReconciliationStatus.MISMATCHED,
                    ReconciliationStatus.INVESTIGATION, ReconciliationStatus.RESOLVED);

    private final LedgerEntryRepository ledgerEntryRepository;
    private final ReconciliationRecordRepository reconciliationRecordRepository;
    private final ExternalFeedGenerator externalFeedGenerator;
    private final OutboxWriter outboxWriter;
    private final AuditEventPublisher auditEventPublisher;

    public ReconciliationService(LedgerEntryRepository ledgerEntryRepository,
                                  ReconciliationRecordRepository reconciliationRecordRepository,
                                  ExternalFeedGenerator externalFeedGenerator, OutboxWriter outboxWriter,
                                  AuditEventPublisher auditEventPublisher) {
        this.ledgerEntryRepository = ledgerEntryRepository;
        this.reconciliationRecordRepository = reconciliationRecordRepository;
        this.externalFeedGenerator = externalFeedGenerator;
        this.outboxWriter = outboxWriter;
        this.auditEventPublisher = auditEventPublisher;
    }

    @Transactional
    public RunSummary run() {
        UUID runId = UUID.randomUUID();
        List<UUID> allTransactionIds = ledgerEntryRepository.findAllDistinctTransactionIds();
        Set<UUID> alreadyTerminal = reconciliationRecordRepository.findAll().stream()
                .filter(r -> TERMINAL_FOR_JOB.contains(r.getStatus()))
                .map(ReconciliationRecord::getTransactionId)
                .collect(Collectors.toSet());

        int matched = 0;
        int mismatched = 0;
        int pending = 0;

        for (UUID transactionId : allTransactionIds) {
            if (alreadyTerminal.contains(transactionId)) {
                continue;
            }
            LedgerEntry debitSide = debitEntryFor(transactionId);
            if (debitSide == null) {
                continue;
            }

            ReconciliationRecord record = reconciliationRecordRepository.findByTransactionId(transactionId)
                    .orElseGet(() -> new ReconciliationRecord(transactionId, debitSide.getAmount(),
                            debitSide.getCurrency(), runId));

            ExternalTransaction external = externalFeedGenerator.generateIfAbsent(transactionId,
                    debitSide.getAmount(), debitSide.getCurrency());

            // Captured fresh, after generation — not once at the top of run() — so a same-instant
            // "immediate" match (see ExternalFeedGenerator's Javadoc) is never misclassified
            // PENDING purely because the generator's own Instant.now() call happened microseconds
            // after a pre-loop snapshot would have.
            if (!external.isAvailable(Instant.now())) {
                record.markPending(runId);
                pending++;
            } else if (matches(debitSide, external)) {
                record.markMatched(external, runId);
                matched++;
            } else {
                String reason = "internal " + debitSide.getAmount() + " " + debitSide.getCurrency()
                        + " vs external " + external.getAmount() + " " + external.getCurrency();
                record.markMismatched(external, reason, runId);
                mismatched++;
                auditEventPublisher.record(AuditAction.RECONCILIATION_MISMATCH_DETECTED, "reconciliation_record",
                        record.getId(), null, null, "MISMATCHED", reason);
            }
            reconciliationRecordRepository.save(record);
        }

        RunSummary summary = new RunSummary(runId, allTransactionIds.size(), matched, mismatched, pending);
        outboxWriter.write("reconciliation.completed", "reconciliation_run", runId, summary);
        log.info("Reconciliation run {} complete: {} transactions, {} matched, {} mismatched, {} pending",
                runId, summary.totalTransactions(), matched, mismatched, pending);
        return summary;
    }

    @Transactional(readOnly = true)
    public ReconciliationRecord getOrThrow(UUID id) {
        return reconciliationRecordRepository.findById(id).orElseThrow(ReconciliationRecordNotFoundException::new);
    }

    @Transactional(readOnly = true)
    public Page<ReconciliationRecord> queue(ReconciliationStatus status, Pageable pageable) {
        return status != null
                ? reconciliationRecordRepository.findByStatus(status, pageable)
                : reconciliationRecordRepository.findAll(pageable);
    }

    @Transactional
    public ReconciliationRecord startInvestigation(UUID id, UUID actorId) {
        ReconciliationRecord record = getOrThrow(id);
        if (record.getStatus() != ReconciliationStatus.MISMATCHED) {
            throw new InvalidReconciliationTransitionException(
                    "Only a MISMATCHED record can be picked up for investigation (current status: "
                            + record.getStatus() + ")");
        }
        record.startInvestigation(actorId);
        return reconciliationRecordRepository.save(record);
    }

    @Transactional
    public ReconciliationRecord resolve(UUID id, UUID actorId, String notes) {
        ReconciliationRecord record = getOrThrow(id);
        if (record.getStatus() != ReconciliationStatus.INVESTIGATION) {
            throw new InvalidReconciliationTransitionException(
                    "Only a record UNDER_INVESTIGATION can be resolved (current status: " + record.getStatus() + ")");
        }
        record.resolve(actorId, notes);
        ReconciliationRecord saved = reconciliationRecordRepository.save(record);
        auditEventPublisher.record(AuditAction.RECONCILIATION_RESOLVED, "reconciliation_record", saved.getId(),
                actorId, null, "RESOLVED", notes);
        return saved;
    }

    private LedgerEntry debitEntryFor(UUID transactionId) {
        return ledgerEntryRepository.findByTransactionId(transactionId).stream()
                .filter(e -> e.getEntryType() == EntryType.DEBIT)
                .findFirst()
                .orElse(null);
    }

    private boolean matches(LedgerEntry internal, ExternalTransaction external) {
        return internal.getAmount().compareTo(external.getAmount()) == 0
                && internal.getCurrency().equals(external.getCurrency());
    }

    public record RunSummary(UUID runId, int totalTransactions, int matched, int mismatched, int pending) {
    }
}
