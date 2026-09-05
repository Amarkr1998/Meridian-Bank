package com.meridianbank.notification.ingest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.meridianbank.notification.domain.NotificationType;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;

/** Proves each topic's payload is parsed correctly and mapped to the right NotificationType —
 *  the actual persistence/idempotency is NotificationIngestService's own responsibility, mocked
 *  out here. */
@ExtendWith(MockitoExtension.class)
class NotificationEventListenerTest {

    @Mock private NotificationIngestService ingestService;

    private NotificationEventListener listener;

    @BeforeEach
    void setUp() {
        listener = new NotificationEventListener(ingestService, new ObjectMapper());
    }

    private ConsumerRecord<String, String> record(String topic, String json) {
        return new ConsumerRecord<>(topic, 0, 0L, "key", json);
    }

    private String envelope(UUID eventId, String payloadJson) {
        return """
                {"eventId":"%s","eventType":"x","eventVersion":1,"occurredAt":"2026-09-04T12:00:00Z",
                 "correlationId":"c","producedBy":"x","payload":%s}
                """.formatted(eventId, payloadJson);
    }

    @Test
    void onPaymentCompleted_extractsCustomerIdAmountAndCurrency() {
        UUID eventId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        String json = envelope(eventId, "{\"customerId\":\"%s\",\"amount\":\"100.00\",\"currency\":\"USD\"}"
                .formatted(customerId));

        listener.onPaymentCompleted(record("payment.completed", json));

        verify(ingestService).ingest(eq(eventId), eq(customerId), eq(NotificationType.PAYMENT_SUCCESS), anyString(),
                contains("100.00 USD"));
    }

    @Test
    void onPaymentFailed_usesReasonAsBody() {
        UUID eventId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        String json = envelope(eventId, "{\"customerId\":\"%s\",\"code\":\"INSUFFICIENT_BALANCE\",\"reason\":\"Not enough funds\"}"
                .formatted(customerId));

        listener.onPaymentFailed(record("payment.failed", json));

        verify(ingestService).ingest(eq(eventId), eq(customerId), eq(NotificationType.PAYMENT_FAILED), anyString(),
                eq("Not enough funds"));
    }

    @Test
    void onKycUpdated_mapsToKycStatusChanged() {
        UUID eventId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        String json = envelope(eventId, "{\"customerId\":\"%s\",\"status\":\"KYC_VERIFIED\"}".formatted(customerId));

        listener.onKycUpdated(record("kyc.updated", json));

        verify(ingestService).ingest(eq(eventId), eq(customerId), eq(NotificationType.KYC_STATUS_CHANGED), anyString(),
                contains("KYC_VERIFIED"));
    }

    @Test
    void onAccountApproved_mapsToAccountStatusChanged() {
        UUID eventId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        String json = envelope(eventId, "{\"customerId\":\"%s\",\"accountType\":\"SAVINGS\"}".formatted(customerId));

        listener.onAccountApproved(record("account.approved", json));

        verify(ingestService).ingest(eq(eventId), eq(customerId), eq(NotificationType.ACCOUNT_STATUS_CHANGED),
                anyString(), contains("SAVINGS"));
    }

    @Test
    void onFraudDetected_mapsToFraudAlert() {
        UUID eventId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        String json = envelope(eventId, "{\"customerId\":\"%s\",\"decisionOrSignal\":\"BLOCK\"}".formatted(customerId));

        listener.onFraudDetected(record("fraud.detected", json));

        verify(ingestService).ingest(eq(eventId), eq(customerId), eq(NotificationType.FRAUD_ALERT), anyString(),
                contains("BLOCK"));
    }

    @Test
    void onNotificationRequested_usesTypeTitleAndBodyDirectly() {
        UUID eventId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        String json = envelope(eventId,
                "{\"customerId\":\"%s\",\"type\":\"SUPPORT_REQUEST_RESOLVED\",\"title\":\"Ticket resolved\",\"body\":\"details\"}"
                        .formatted(customerId));

        listener.onNotificationRequested(record("notification.requested", json));

        verify(ingestService).ingest(eventId, customerId, NotificationType.SUPPORT_REQUEST_RESOLVED,
                "Ticket resolved", "details");
    }

    @Test
    void onNotificationRequested_unrecognizedType_defaultsGracefully() {
        UUID eventId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        String json = envelope(eventId,
                "{\"customerId\":\"%s\",\"type\":\"SOMETHING_UNKNOWN\",\"title\":\"t\",\"body\":\"b\"}"
                        .formatted(customerId));

        listener.onNotificationRequested(record("notification.requested", json));

        verify(ingestService).ingest(eq(eventId), eq(customerId), eq(NotificationType.SUPPORT_REQUEST_RESOLVED),
                anyString(), anyString());
    }

    @Test
    void missingCustomerId_throwsMalformedNotificationEventException() {
        UUID eventId = UUID.randomUUID();
        String json = envelope(eventId, "{\"amount\":\"1.00\",\"currency\":\"USD\"}");

        assertThrows(MalformedNotificationEventException.class,
                () -> listener.onPaymentCompleted(record("payment.completed", json)));
    }

    @Test
    void malformedJson_throwsMalformedNotificationEventException() {
        assertThrows(MalformedNotificationEventException.class,
                () -> listener.onPaymentCompleted(record("payment.completed", "not json")));
    }
}
