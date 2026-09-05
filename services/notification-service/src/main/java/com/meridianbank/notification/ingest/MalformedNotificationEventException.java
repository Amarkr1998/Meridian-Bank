package com.meridianbank.notification.ingest;

/**
 * A structurally-broken consumed message (unparseable JSON, or missing a field this consumer
 * treats as mandatory — {@code eventId} or {@code customerId}). Propagates to the listener
 * container's retry/backoff/DLQ policy — see NotificationKafkaConfig — rather than being caught
 * here; a genuine "poison pill" still goes through the same retry-then-DLQ path as any other
 * failure, since the two are indistinguishable to the consumer ahead of time.
 */
public class MalformedNotificationEventException extends RuntimeException {

    public MalformedNotificationEventException(String message, Throwable cause) {
        super(message, cause);
    }
}
