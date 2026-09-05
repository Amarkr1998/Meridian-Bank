package com.meridianbank.audit.ingest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.meridianbank.audit.domain.AuditEvent;
import com.meridianbank.audit.repository.AuditEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * Turns one {@code audit.event} Kafka message into a durable {@link AuditEvent} row — the only
 * write path into this table anywhere in the service (see docs/adr/0012-audit-architecture.md).
 * Idempotent by the envelope's {@code eventId}: Kafka delivery is at-least-once, not
 * exactly-once, so a redelivered message (consumer restart after a commit race, a producer retry
 * that also eventually succeeded, etc.) must not create a second row — see
 * docs/architecture/kafka-architecture.md ("Idempotent consumers").
 *
 * <p>Deliberately lenient about the payload shape (additive schema evolution — see
 * docs/kafka/topics.md's "Event Envelope Convention"): every payload field is read defensively and
 * missing/malformed sub-fields fall back to {@code null} rather than failing the whole message,
 * since a partially-populated audit row is far more useful than an event silently routed to the
 * DLQ over one optional field. Only a structurally-broken envelope (unparseable JSON, or missing
 * the handful of fields this class treats as mandatory — see {@link MalformedAuditEventException})
 * is treated as a hard failure, which propagates to {@link AuditEventListener}'s retry/DLQ policy.
 */
@Service
public class AuditIngestService {

    private static final Logger log = LoggerFactory.getLogger(AuditIngestService.class);
    private static final int SUPPORTED_EVENT_VERSION = 1;

    private final AuditEventRepository repository;
    private final ObjectMapper objectMapper;

    public AuditIngestService(AuditEventRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public void ingest(String rawEnvelopeJson) {
        JsonNode envelope;
        try {
            envelope = objectMapper.readTree(rawEnvelopeJson);
        } catch (Exception e) {
            throw new MalformedAuditEventException("Envelope is not valid JSON", e);
        }

        UUID eventId = readRequiredUuid(envelope, "eventId");
        String eventType = readRequiredText(envelope, "eventType");
        int eventVersion = envelope.path("eventVersion").asInt(SUPPORTED_EVENT_VERSION);
        if (eventVersion > SUPPORTED_EVENT_VERSION) {
            log.warn("audit.event {} carries eventVersion {} newer than this consumer supports ({}) — "
                    + "processing best-effort per the additive-schema-evolution policy", eventId, eventVersion,
                    SUPPORTED_EVENT_VERSION);
        }
        Instant occurredAt = readInstant(envelope, "occurredAt");
        String correlationId = envelope.path("correlationId").asText(null);
        String producedBy = readRequiredText(envelope, "producedBy");
        JsonNode payload = envelope.path("payload");

        if (repository.existsByEventId(eventId)) {
            log.debug("audit.event {} already ingested — idempotent no-op (redelivery)", eventId);
            return;
        }

        UUID actorId = readOptionalUuid(payload, "actorId");
        String actorRole = payload.path("actorRole").asText(null);
        String action = payload.hasNonNull("action") ? payload.get("action").asText() : eventType;
        String resourceType = payload.path("resourceType").asText("unknown");
        UUID resourceId = readOptionalUuid(payload, "resourceId");
        String result = payload.path("result").asText("UNKNOWN");
        String detail = payload.path("detail").asText(null);

        AuditEvent event = new AuditEvent(eventId, eventType, occurredAt, correlationId, producedBy, actorId,
                actorRole, action, resourceType, resourceId, result, detail);
        try {
            repository.saveAndFlush(event);
        } catch (DataIntegrityViolationException e) {
            // A concurrent delivery of the same eventId lost the existsByEventId race — the unique
            // constraint on event_id is the durable backstop; treat it the same as the check above.
            log.debug("audit.event {} hit the event_id unique constraint — idempotent no-op (race)", eventId);
        }
    }

    private String readRequiredText(JsonNode envelope, String field) {
        if (!envelope.hasNonNull(field)) {
            throw new MalformedAuditEventException("Missing required envelope field: " + field, null);
        }
        return envelope.get(field).asText();
    }

    private UUID readRequiredUuid(JsonNode envelope, String field) {
        try {
            return UUID.fromString(readRequiredText(envelope, field));
        } catch (IllegalArgumentException e) {
            throw new MalformedAuditEventException("Envelope field " + field + " is not a valid UUID", e);
        }
    }

    private UUID readOptionalUuid(JsonNode node, String field) {
        if (!node.hasNonNull(field)) {
            return null;
        }
        try {
            return UUID.fromString(node.get(field).asText());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private Instant readInstant(JsonNode envelope, String field) {
        if (!envelope.hasNonNull(field)) {
            return Instant.now();
        }
        try {
            return Instant.parse(envelope.get(field).asText());
        } catch (Exception e) {
            return Instant.now();
        }
    }
}
