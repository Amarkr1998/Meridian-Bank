package com.meridianbank.audit.ingest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.meridianbank.audit.domain.AuditEvent;
import com.meridianbank.audit.repository.AuditEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Proves the two invariants that matter most for a Kafka consumer: idempotent de-duplication by
 *  eventId, and tolerant-but-not-silent handling of the payload shape (see AuditIngestService's
 *  Javadoc for the additive-schema-evolution rationale). */
@ExtendWith(MockitoExtension.class)
class AuditIngestServiceTest {

    @Mock private AuditEventRepository repository;

    private AuditIngestService service;

    @BeforeEach
    void setUp() {
        service = new AuditIngestService(repository, new ObjectMapper());
    }

    private String envelope(String eventId, String action, String actorId) {
        return """
                {
                  "eventId": "%s",
                  "eventType": "audit.event",
                  "eventVersion": 1,
                  "occurredAt": "2026-09-04T12:00:00Z",
                  "correlationId": "corr-1",
                  "producedBy": "payment-service",
                  "payload": {
                    "actorId": "%s",
                    "actorRole": "CUSTOMER",
                    "action": "%s",
                    "resourceType": "transaction",
                    "resourceId": "%s",
                    "result": "SUCCESS",
                    "detail": "test"
                  }
                }
                """.formatted(eventId, actorId, action, UUID.randomUUID());
    }

    @Test
    void ingest_newEvent_savesARow() {
        UUID eventId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        when(repository.existsByEventId(eventId)).thenReturn(false);

        service.ingest(envelope(eventId.toString(), "PAYMENT_COMPLETED", actorId.toString()));

        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(repository).saveAndFlush(captor.capture());
        AuditEvent saved = captor.getValue();
        assertThat(saved.getEventId()).isEqualTo(eventId);
        assertThat(saved.getActorId()).isEqualTo(actorId);
        assertThat(saved.getAction()).isEqualTo("PAYMENT_COMPLETED");
        assertThat(saved.getProducedBy()).isEqualTo("payment-service");
        assertThat(saved.getResult()).isEqualTo("SUCCESS");
    }

    @Test
    void ingest_alreadySeenEventId_isANoOp() {
        UUID eventId = UUID.randomUUID();
        when(repository.existsByEventId(eventId)).thenReturn(true);

        service.ingest(envelope(eventId.toString(), "PAYMENT_COMPLETED", UUID.randomUUID().toString()));

        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void ingest_concurrentDuplicateInsert_isToleratedNotThrown() {
        UUID eventId = UUID.randomUUID();
        when(repository.existsByEventId(eventId)).thenReturn(false);
        when(repository.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("duplicate"));

        service.ingest(envelope(eventId.toString(), "PAYMENT_COMPLETED", UUID.randomUUID().toString()));
        // no exception propagates — the unique constraint race is treated as a successful no-op
    }

    @Test
    void ingest_malformedJson_throwsMalformedAuditEventException() {
        assertThrows(MalformedAuditEventException.class, () -> service.ingest("not json"));
        verifyNoInteractions(repository);
    }

    @Test
    void ingest_missingRequiredEventId_throwsMalformedAuditEventException() {
        String badEnvelope = """
                {"eventType": "audit.event", "producedBy": "payment-service", "payload": {}}
                """;
        assertThrows(MalformedAuditEventException.class, () -> service.ingest(badEnvelope));
        verifyNoInteractions(repository);
    }

    @Test
    void ingest_payloadMissingOptionalFields_defaultsGracefullyRatherThanFailing() {
        UUID eventId = UUID.randomUUID();
        when(repository.existsByEventId(eventId)).thenReturn(false);
        String minimalEnvelope = """
                {
                  "eventId": "%s",
                  "eventType": "audit.event",
                  "occurredAt": "2026-09-04T12:00:00Z",
                  "producedBy": "fraud-risk-service",
                  "payload": { "action": "FRAUD_ALERT_CREATED", "resourceType": "fraud_alert" }
                }
                """.formatted(eventId);

        service.ingest(minimalEnvelope);

        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(repository).saveAndFlush(captor.capture());
        AuditEvent saved = captor.getValue();
        assertThat(saved.getActorId()).isNull();
        assertThat(saved.getActorRole()).isNull();
        assertThat(saved.getResult()).isEqualTo("UNKNOWN");
        assertThat(saved.getAction()).isEqualTo("FRAUD_ALERT_CREATED");
    }
}
