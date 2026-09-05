package com.meridianbank.audit.ingest;

/**
 * A structurally-broken {@code audit.event} message (unparseable JSON, or missing one of the
 * handful of envelope fields this consumer treats as mandatory). Thrown from
 * {@link AuditIngestService}, propagates up to {@link AuditEventListener} where Spring Kafka's
 * retry/backoff policy applies and, on exhaustion, routes the raw message to {@code audit.event.DLQ}
 * — this is a genuine "poison pill" (retrying it again will never succeed), but it is still routed
 * through the same retry-then-DLQ path as any other failure rather than special-cased, since the
 * two are indistinguishable to the consumer ahead of time and DLQ routing is the correct outcome
 * either way.
 */
public class MalformedAuditEventException extends RuntimeException {

    public MalformedAuditEventException(String message, Throwable cause) {
        super(message, cause);
    }
}
