package com.meridianbank.audit.web.dto;

import com.meridianbank.audit.domain.AuditEvent;

import java.time.Instant;
import java.util.UUID;

public record AuditEventResponse(
        UUID id, UUID eventId, String eventType, Instant occurredAt, String correlationId, String producedBy,
        UUID actorId, String actorRole, String action, String resourceType, UUID resourceId, String result,
        String detail, Instant receivedAt
) {
    public static AuditEventResponse from(AuditEvent e) {
        return new AuditEventResponse(e.getId(), e.getEventId(), e.getEventType(), e.getOccurredAt(),
                e.getCorrelationId(), e.getProducedBy(), e.getActorId(), e.getActorRole(), e.getAction(),
                e.getResourceType(), e.getResourceId(), e.getResult(), e.getDetail(), e.getReceivedAt());
    }
}
