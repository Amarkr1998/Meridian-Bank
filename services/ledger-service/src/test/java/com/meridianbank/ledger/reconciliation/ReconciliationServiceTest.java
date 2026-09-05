package com.meridianbank.ledger.reconciliation;

import com.meridianbank.ledger.audit.AuditEventPublisher;
import com.meridianbank.ledger.domain.EntryType;
import com.meridianbank.ledger.domain.LedgerEntry;
import com.meridianbank.ledger.outbox.OutboxWriter;
import com.meridianbank.ledger.repository.LedgerEntryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReconciliationServiceTest {

    @Mock private LedgerEntryRepository ledgerEntryRepository;
    @Mock private ReconciliationRecordRepository reconciliationRecordRepository;
    @Mock private ExternalFeedGenerator externalFeedGenerator;
    @Mock private OutboxWriter outboxWriter;
    @Mock private AuditEventPublisher auditEventPublisher;

    private ReconciliationService service;

    @BeforeEach
    void setUp() {
        service = new ReconciliationService(ledgerEntryRepository, reconciliationRecordRepository,
                externalFeedGenerator, outboxWriter, auditEventPublisher);
        lenient().when(reconciliationRecordRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    private LedgerEntry debitEntry(UUID transactionId, BigDecimal amount, String currency) {
        return new LedgerEntry(transactionId, UUID.randomUUID(), EntryType.DEBIT, amount, currency, null);
    }

    @Test
    void run_exactlyMatchingExternalRecord_classifiesMatched() {
        UUID txnId = UUID.randomUUID();
        when(ledgerEntryRepository.findAllDistinctTransactionIds()).thenReturn(List.of(txnId));
        when(ledgerEntryRepository.findByTransactionId(txnId))
                .thenReturn(List.of(debitEntry(txnId, new BigDecimal("100.00"), "USD")));
        when(reconciliationRecordRepository.findAll()).thenReturn(List.of());
        when(reconciliationRecordRepository.findByTransactionId(txnId)).thenReturn(Optional.empty());
        ExternalTransaction ext = new ExternalTransaction(txnId, new BigDecimal("100.00"), "USD", Instant.now());
        when(externalFeedGenerator.generateIfAbsent(txnId, new BigDecimal("100.00"), "USD")).thenReturn(ext);

        ReconciliationService.RunSummary summary = service.run();

        assertThat(summary.matched()).isEqualTo(1);
        assertThat(summary.mismatched()).isEqualTo(0);
        assertThat(summary.pending()).isEqualTo(0);
        verify(reconciliationRecordRepository).save(argThat(r -> r.getStatus() == ReconciliationStatus.MATCHED));
        verify(outboxWriter).write(eq("reconciliation.completed"), anyString(), any(), any());
        verifyNoInteractions(auditEventPublisher);
    }

    @Test
    void run_wrongExternalAmount_classifiesMismatchedAndAudits() {
        UUID txnId = UUID.randomUUID();
        when(ledgerEntryRepository.findAllDistinctTransactionIds()).thenReturn(List.of(txnId));
        when(ledgerEntryRepository.findByTransactionId(txnId))
                .thenReturn(List.of(debitEntry(txnId, new BigDecimal("100.00"), "USD")));
        when(reconciliationRecordRepository.findAll()).thenReturn(List.of());
        when(reconciliationRecordRepository.findByTransactionId(txnId)).thenReturn(Optional.empty());
        ExternalTransaction ext = new ExternalTransaction(txnId, new BigDecimal("103.50"), "USD", Instant.now());
        when(externalFeedGenerator.generateIfAbsent(txnId, new BigDecimal("100.00"), "USD")).thenReturn(ext);

        ReconciliationService.RunSummary summary = service.run();

        assertThat(summary.mismatched()).isEqualTo(1);
        verify(reconciliationRecordRepository).save(argThat(r -> r.getStatus() == ReconciliationStatus.MISMATCHED));
        verify(auditEventPublisher).record(eq(com.meridianbank.ledger.audit.AuditAction.RECONCILIATION_MISMATCH_DETECTED),
                anyString(), any(), any(), any(), anyString(), anyString());
    }

    @Test
    void run_externalRecordNotYetAvailable_classifiesPending() {
        UUID txnId = UUID.randomUUID();
        when(ledgerEntryRepository.findAllDistinctTransactionIds()).thenReturn(List.of(txnId));
        when(ledgerEntryRepository.findByTransactionId(txnId))
                .thenReturn(List.of(debitEntry(txnId, new BigDecimal("100.00"), "USD")));
        when(reconciliationRecordRepository.findAll()).thenReturn(List.of());
        when(reconciliationRecordRepository.findByTransactionId(txnId)).thenReturn(Optional.empty());
        ExternalTransaction ext = new ExternalTransaction(txnId, new BigDecimal("100.00"), "USD",
                Instant.now().plusSeconds(120));
        when(externalFeedGenerator.generateIfAbsent(txnId, new BigDecimal("100.00"), "USD")).thenReturn(ext);

        ReconciliationService.RunSummary summary = service.run();

        assertThat(summary.pending()).isEqualTo(1);
        verify(reconciliationRecordRepository).save(argThat(r -> r.getStatus() == ReconciliationStatus.PENDING));
    }

    @Test
    void run_recordAlreadyMatched_isNeverReprocessed() {
        UUID txnId = UUID.randomUUID();
        when(ledgerEntryRepository.findAllDistinctTransactionIds()).thenReturn(List.of(txnId));
        ReconciliationRecord existing = new ReconciliationRecord(txnId, new BigDecimal("100.00"), "USD",
                UUID.randomUUID());
        existing.markMatched(new ExternalTransaction(txnId, new BigDecimal("100.00"), "USD", Instant.now()),
                UUID.randomUUID());
        when(reconciliationRecordRepository.findAll()).thenReturn(List.of(existing));

        service.run();

        verifyNoInteractions(externalFeedGenerator);
        verify(reconciliationRecordRepository, never()).save(any());
    }

    @Test
    void startInvestigation_onAMismatchedRecord_transitionsToInvestigation() {
        UUID txnId = UUID.randomUUID();
        ReconciliationRecord record = new ReconciliationRecord(txnId, new BigDecimal("100.00"), "USD",
                UUID.randomUUID());
        record.markMismatched(new ExternalTransaction(txnId, new BigDecimal("90.00"), "USD", Instant.now()),
                "bad", UUID.randomUUID());
        when(reconciliationRecordRepository.findById(record.getId())).thenReturn(Optional.of(record));
        UUID actorId = UUID.randomUUID();

        ReconciliationRecord result = service.startInvestigation(record.getId(), actorId);

        assertThat(result.getStatus()).isEqualTo(ReconciliationStatus.INVESTIGATION);
        assertThat(result.getInvestigatedBy()).isEqualTo(actorId);
    }

    @Test
    void startInvestigation_onAPendingRecord_throws() {
        UUID txnId = UUID.randomUUID();
        ReconciliationRecord record = new ReconciliationRecord(txnId, new BigDecimal("100.00"), "USD",
                UUID.randomUUID());
        when(reconciliationRecordRepository.findById(record.getId())).thenReturn(Optional.of(record));

        assertThrows(InvalidReconciliationTransitionException.class,
                () -> service.startInvestigation(record.getId(), UUID.randomUUID()));
    }

    @Test
    void resolve_onAnInvestigatedRecord_transitionsToResolvedAndAudits() {
        UUID txnId = UUID.randomUUID();
        ReconciliationRecord record = new ReconciliationRecord(txnId, new BigDecimal("100.00"), "USD",
                UUID.randomUUID());
        record.markMismatched(new ExternalTransaction(txnId, new BigDecimal("90.00"), "USD", Instant.now()),
                "bad", UUID.randomUUID());
        record.startInvestigation(UUID.randomUUID());
        when(reconciliationRecordRepository.findById(record.getId())).thenReturn(Optional.of(record));
        UUID actorId = UUID.randomUUID();

        ReconciliationRecord result = service.resolve(record.getId(), actorId, "external system was late");

        assertThat(result.getStatus()).isEqualTo(ReconciliationStatus.RESOLVED);
        assertThat(result.getResolvedBy()).isEqualTo(actorId);
        verify(auditEventPublisher).record(eq(com.meridianbank.ledger.audit.AuditAction.RECONCILIATION_RESOLVED),
                anyString(), eq(record.getId()), eq(actorId), any(), eq("RESOLVED"), anyString());
    }

    @Test
    void resolve_onAMismatchedRecordNotYetUnderInvestigation_throws() {
        UUID txnId = UUID.randomUUID();
        ReconciliationRecord record = new ReconciliationRecord(txnId, new BigDecimal("100.00"), "USD",
                UUID.randomUUID());
        record.markMismatched(new ExternalTransaction(txnId, new BigDecimal("90.00"), "USD", Instant.now()),
                "bad", UUID.randomUUID());
        when(reconciliationRecordRepository.findById(record.getId())).thenReturn(Optional.of(record));

        assertThrows(InvalidReconciliationTransitionException.class,
                () -> service.resolve(record.getId(), UUID.randomUUID(), "notes"));
        verifyNoInteractions(auditEventPublisher);
    }
}
