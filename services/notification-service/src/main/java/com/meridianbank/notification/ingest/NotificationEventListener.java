package com.meridianbank.notification.ingest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.meridianbank.notification.domain.NotificationType;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * One {@code @KafkaListener} per consumed topic, all under this service's own consumer group
 * (see application.yml) — the second real Kafka consumer in this project, after audit-service
 * (Phase 11). Each method extracts just enough from its topic's payload to build a human-readable
 * notification and hands off to {@link NotificationIngestService}; a thrown exception is caught
 * by the container's {@link org.springframework.kafka.listener.DefaultErrorHandler} (see
 * NotificationKafkaConfig), which retries with backoff and eventually routes to
 * {@code <topic>.DLQ} — these methods never catch or swallow anything themselves.
 *
 * <p>Payload parsing is deliberately lenient (defensive field extraction via {@link JsonNode},
 * matching audit-service's {@code AuditIngestService} precedent) — a missing optional field falls
 * back to a sensible default rather than failing the whole message; only a missing
 * {@code customerId} (nothing useful can be notified without it) is a hard failure.
 */
@Component
public class NotificationEventListener {

    private static final Logger log = LoggerFactory.getLogger(NotificationEventListener.class);

    private final NotificationIngestService ingestService;
    private final ObjectMapper objectMapper;

    public NotificationEventListener(NotificationIngestService ingestService, ObjectMapper objectMapper) {
        this.ingestService = ingestService;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = "payment.completed")
    public void onPaymentCompleted(ConsumerRecord<String, String> record) {
        JsonNode envelope = parse(record.value());
        JsonNode payload = envelope.path("payload");
        UUID customerId = requireUuid(payload, "customerId");
        String amount = payload.path("amount").asText("");
        String currency = payload.path("currency").asText("");
        ingestService.ingest(eventId(envelope), customerId, NotificationType.PAYMENT_SUCCESS, "Payment completed",
                "Your payment of " + amount + " " + currency + " completed successfully.");
    }

    @KafkaListener(topics = "payment.failed")
    public void onPaymentFailed(ConsumerRecord<String, String> record) {
        JsonNode envelope = parse(record.value());
        JsonNode payload = envelope.path("payload");
        UUID customerId = requireUuid(payload, "customerId");
        String reason = payload.path("reason").asText("Please check your account for details.");
        ingestService.ingest(eventId(envelope), customerId, NotificationType.PAYMENT_FAILED, "Payment failed",
                reason);
    }

    @KafkaListener(topics = "kyc.updated")
    public void onKycUpdated(ConsumerRecord<String, String> record) {
        JsonNode envelope = parse(record.value());
        JsonNode payload = envelope.path("payload");
        UUID customerId = requireUuid(payload, "customerId");
        String status = payload.path("status").asText("UPDATED");
        ingestService.ingest(eventId(envelope), customerId, NotificationType.KYC_STATUS_CHANGED,
                "KYC status updated", "Your KYC verification status is now " + status + ".");
    }

    @KafkaListener(topics = "account.approved")
    public void onAccountApproved(ConsumerRecord<String, String> record) {
        JsonNode envelope = parse(record.value());
        JsonNode payload = envelope.path("payload");
        UUID customerId = requireUuid(payload, "customerId");
        String accountType = payload.path("accountType").asText("account");
        ingestService.ingest(eventId(envelope), customerId, NotificationType.ACCOUNT_STATUS_CHANGED,
                "Account activated", "Your " + accountType + " account is now active.");
    }

    @KafkaListener(topics = "fraud.detected")
    public void onFraudDetected(ConsumerRecord<String, String> record) {
        JsonNode envelope = parse(record.value());
        JsonNode payload = envelope.path("payload");
        UUID customerId = requireUuid(payload, "customerId");
        String decisionOrSignal = payload.path("decisionOrSignal").asText("flagged");
        ingestService.ingest(eventId(envelope), customerId, NotificationType.FRAUD_ALERT,
                "Account activity flagged for review",
                "A recent transaction was flagged (" + decisionOrSignal + "). Contact support if this wasn't you.");
    }

    @KafkaListener(topics = "notification.requested")
    public void onNotificationRequested(ConsumerRecord<String, String> record) {
        JsonNode envelope = parse(record.value());
        JsonNode payload = envelope.path("payload");
        UUID customerId = requireUuid(payload, "customerId");
        NotificationType type = parseType(payload.path("type").asText(null));
        String title = payload.path("title").asText("Notification");
        String body = payload.path("body").asText("");
        ingestService.ingest(eventId(envelope), customerId, type, title, body);
    }

    private NotificationType parseType(String raw) {
        if (raw == null) {
            return NotificationType.SUPPORT_REQUEST_RESOLVED;
        }
        try {
            return NotificationType.valueOf(raw);
        } catch (IllegalArgumentException e) {
            log.warn("Unrecognized notification.requested type '{}' — defaulting to SUPPORT_REQUEST_RESOLVED", raw);
            return NotificationType.SUPPORT_REQUEST_RESOLVED;
        }
    }

    private JsonNode parse(String rawEnvelopeJson) {
        try {
            return objectMapper.readTree(rawEnvelopeJson);
        } catch (Exception e) {
            throw new MalformedNotificationEventException("Envelope is not valid JSON", e);
        }
    }

    private UUID eventId(JsonNode envelope) {
        if (!envelope.hasNonNull("eventId")) {
            throw new MalformedNotificationEventException("Missing required envelope field: eventId", null);
        }
        try {
            return UUID.fromString(envelope.get("eventId").asText());
        } catch (IllegalArgumentException e) {
            throw new MalformedNotificationEventException("Envelope field eventId is not a valid UUID", e);
        }
    }

    private UUID requireUuid(JsonNode payload, String field) {
        if (!payload.hasNonNull(field)) {
            throw new MalformedNotificationEventException("Missing required payload field: " + field, null);
        }
        try {
            return UUID.fromString(payload.get(field).asText());
        } catch (IllegalArgumentException e) {
            throw new MalformedNotificationEventException("Payload field " + field + " is not a valid UUID", e);
        }
    }
}
