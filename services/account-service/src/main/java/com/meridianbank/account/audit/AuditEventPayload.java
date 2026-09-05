package com.meridianbank.account.audit;

import java.util.UUID;

/**
 * The {@code audit.event} payload shape every Meridian Bank service publishes — see
 * docs/adr/0012-audit-architecture.md and audit-service's {@code AuditIngestService}, which reads
 * exactly these fields (defensively — see its Javadoc). Duplicated per service, not shared.
 */
public record AuditEventPayload(UUID actorId, String actorRole, String action, String resourceType,
                                 UUID resourceId, String result, String detail) {
}
