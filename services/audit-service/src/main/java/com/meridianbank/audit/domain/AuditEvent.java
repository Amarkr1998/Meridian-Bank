package com.meridianbank.audit.domain;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

/**
 * One durable, append-only record of a sensitive action performed somewhere in the platform — see
 * docs/adr/0012-audit-architecture.md. There is deliberately no setter beyond construction and no
 * repository update/delete method anywhere in this service (see AuditEventRepository) — the only
 * way this row's fields change after insert is if they never did.
 *
 * <p>{@code eventId} is the producing service's own event identity (the Kafka envelope's
 * {@code eventId} — see docs/kafka/topics.md), unique here so a redelivered message is a no-op,
 * not a duplicate row (see AuditIngestService). {@code id} is this table's own primary key,
 * independent of that.
 */
@Entity
@Table(name = "audit_events")
public class AuditEvent {

    @Id
    private UUID id;

    @Column(name = "event_id", nullable = false, unique = true)
    private UUID eventId;

    @Column(name = "event_type", nullable = false, length = 50)
    private String eventType;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "correlation_id", length = 100)
    private String correlationId;

    @Column(name = "produced_by", nullable = false, length = 50)
    private String producedBy;

    @Column(name = "actor_id")
    private UUID actorId;

    @Column(name = "actor_role", length = 30)
    private String actorRole;

    @Column(nullable = false, length = 50)
    private String action;

    @Column(name = "resource_type", nullable = false, length = 50)
    private String resourceType;

    @Column(name = "resource_id")
    private UUID resourceId;

    @Column(nullable = false, length = 30)
    private String result;

    @Column(length = 1000)
    private String detail;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;

    protected AuditEvent() {
    }

    public AuditEvent(UUID eventId, String eventType, Instant occurredAt, String correlationId, String producedBy,
                       UUID actorId, String actorRole, String action, String resourceType, UUID resourceId,
                       String result, String detail) {
        this.id = UUID.randomUUID();
        this.eventId = eventId;
        this.eventType = eventType;
        this.occurredAt = occurredAt;
        this.correlationId = correlationId;
        this.producedBy = producedBy;
        this.actorId = actorId;
        this.actorRole = actorRole;
        this.action = action;
        this.resourceType = resourceType;
        this.resourceId = resourceId;
        this.result = result;
        this.detail = detail;
        this.receivedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getEventId() {
        return eventId;
    }

    public String getEventType() {
        return eventType;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public String getCorrelationId() {
        return correlationId;
    }

    public String getProducedBy() {
        return producedBy;
    }

    public UUID getActorId() {
        return actorId;
    }

    public String getActorRole() {
        return actorRole;
    }

    public String getAction() {
        return action;
    }

    public String getResourceType() {
        return resourceType;
    }

    public UUID getResourceId() {
        return resourceId;
    }

    public String getResult() {
        return result;
    }

    public String getDetail() {
        return detail;
    }

    public Instant getReceivedAt() {
        return receivedAt;
    }
}
