package com.meridianbank.ledger.outbox;

import java.time.Instant;
import java.util.UUID;

/** The common event envelope every Meridian Bank service uses — see docs/kafka/topics.md. */
public record EventEnvelope(
        UUID eventId,
        String eventType,
        int eventVersion,
        Instant occurredAt,
        String correlationId,
        String producedBy,
        Object payload
) {
}
