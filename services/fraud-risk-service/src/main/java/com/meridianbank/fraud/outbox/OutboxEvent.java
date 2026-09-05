package com.meridianbank.fraud.outbox;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

/**
 * Written in the same database transaction as the business change it describes (see
 * {@link OutboxWriter}) — never published directly from a request handler. {@link OutboxPublisher}
 * relays PENDING rows to Kafka and marks them SENT; see docs/adr/0005-outbox-pattern.md.
 */
@Entity
@Table(name = "outbox_events")
public class OutboxEvent {

    @Id
    private UUID id;

    @Column(name = "aggregate_type", nullable = false, length = 50)
    private String aggregateType;

    @Column(name = "aggregate_id", nullable = false)
    private UUID aggregateId;

    @Column(name = "event_type", nullable = false, length = 100)
    private String eventType;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OutboxEventStatus status;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "next_attempt_at", nullable = false)
    private Instant nextAttemptAt;

    @Column(name = "sent_at")
    private Instant sentAt;

    protected OutboxEvent() {
    }

    public OutboxEvent(String aggregateType, UUID aggregateId, String eventType, String payload) {
        this.id = UUID.randomUUID();
        this.aggregateType = aggregateType;
        this.aggregateId = aggregateId;
        this.eventType = eventType;
        this.payload = payload;
        this.status = OutboxEventStatus.PENDING;
        this.attempts = 0;
        this.createdAt = Instant.now();
        this.nextAttemptAt = this.createdAt;
    }

    public void markSent() {
        this.status = OutboxEventStatus.SENT;
        this.sentAt = Instant.now();
    }

    public void scheduleRetry(Instant nextAttemptAt) {
        this.attempts++;
        this.nextAttemptAt = nextAttemptAt;
    }

    public void markFailed() {
        this.attempts++;
        this.status = OutboxEventStatus.FAILED;
    }

    public UUID getId() {
        return id;
    }

    public String getAggregateType() {
        return aggregateType;
    }

    public UUID getAggregateId() {
        return aggregateId;
    }

    public String getEventType() {
        return eventType;
    }

    public String getPayload() {
        return payload;
    }

    public OutboxEventStatus getStatus() {
        return status;
    }

    public int getAttempts() {
        return attempts;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getNextAttemptAt() {
        return nextAttemptAt;
    }

    public Instant getSentAt() {
        return sentAt;
    }
}
