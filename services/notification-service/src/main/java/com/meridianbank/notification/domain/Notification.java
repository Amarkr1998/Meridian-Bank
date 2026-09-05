package com.meridianbank.notification.domain;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

/**
 * One in-app notification — the real, queryable artifact this service produces. Simulated
 * email/SMS delivery (see NotificationIngestService) is a logged side effect at ingest time, not
 * a separate persisted row — this service has no real email address or phone number for a
 * customer (that lives in customer-kyc-service) to send to, so "delivery" is necessarily
 * simulated, and modeling separate per-channel rows for a log line would be complexity this demo
 * doesn't need. See notification-service/README.md.
 */
@Entity
@Table(name = "notifications")
public class Notification {

    @Id
    private UUID id;

    @Column(name = "event_id", nullable = false, unique = true)
    private UUID eventId;

    @Column(name = "customer_id", nullable = false)
    private UUID customerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private NotificationType type;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, length = 1000)
    private String body;

    @Column(nullable = false)
    private boolean read;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected Notification() {
    }

    public Notification(UUID eventId, UUID customerId, NotificationType type, String title, String body) {
        this.id = UUID.randomUUID();
        this.eventId = eventId;
        this.customerId = customerId;
        this.type = type;
        this.title = title;
        this.body = body;
        this.read = false;
        this.createdAt = Instant.now();
    }

    public void markRead() {
        this.read = true;
    }

    public UUID getId() {
        return id;
    }

    public UUID getEventId() {
        return eventId;
    }

    public UUID getCustomerId() {
        return customerId;
    }

    public NotificationType getType() {
        return type;
    }

    public String getTitle() {
        return title;
    }

    public String getBody() {
        return body;
    }

    public boolean isRead() {
        return read;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
