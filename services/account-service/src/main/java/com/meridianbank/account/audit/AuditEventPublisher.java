package com.meridianbank.account.audit;

import com.meridianbank.account.outbox.OutboxWriter;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Thin convenience wrapper around the existing {@link OutboxWriter} (see
 * docs/adr/0005-outbox-pattern.md) — an {@code audit.event} row is written in the same DB
 * transaction as the business change it describes, same as every other outbox event this service
 * already publishes, and relayed to Kafka by the same {@code OutboxPublisher}. See
 * docs/adr/0012-audit-architecture.md.
 */
@Component
public class AuditEventPublisher {

    private final OutboxWriter outboxWriter;

    public AuditEventPublisher(OutboxWriter outboxWriter) {
        this.outboxWriter = outboxWriter;
    }

    public void record(AuditAction action, String resourceType, UUID resourceId, UUID actorId, String actorRole,
                        String result, String detail) {
        outboxWriter.write("audit.event", resourceType, resourceId,
                new AuditEventPayload(actorId, actorRole, action.name(), resourceType, resourceId, result, detail));
    }
}
