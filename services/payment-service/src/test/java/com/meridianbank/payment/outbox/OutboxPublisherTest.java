package com.meridianbank.payment.outbox;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Proves the retry-with-backoff and DLQ-on-exhaustion behavior documented in
 * {@link OutboxPublisher}'s Javadoc, using a mocked {@link KafkaTemplate} so failure/backoff
 * timing is deterministic rather than depending on a real broker outage.
 */
@ExtendWith(MockitoExtension.class)
class OutboxPublisherTest {

    @Mock private OutboxEventRepository repository;
    @Mock private KafkaTemplate<String, String> kafkaTemplate;

    private OutboxPublisher publisher;

    @BeforeEach
    void setUp() {
        // maxAttempts=3, base backoff 2s, max backoff 60s — small numbers so assertions are exact.
        OutboxProperties properties = new OutboxProperties(2000, 20, 3, 2, 60);
        publisher = new OutboxPublisher(repository, kafkaTemplate, properties);
    }

    private OutboxEvent pendingEvent() {
        return new OutboxEvent("transaction", UUID.randomUUID(), "payment.completed", "{\"eventId\":\"x\"}");
    }

    @SuppressWarnings("unchecked")
    private CompletableFuture<SendResult<String, String>> succeeded() {
        return CompletableFuture.completedFuture(mock(SendResult.class));
    }

    private CompletableFuture<SendResult<String, String>> failed() {
        CompletableFuture<SendResult<String, String>> future = new CompletableFuture<>();
        future.completeExceptionally(new RuntimeException("broker unavailable"));
        return future;
    }

    @Test
    void publishPending_onSuccess_marksEventSent() {
        OutboxEvent event = pendingEvent();
        when(repository.findByStatusAndNextAttemptAtLessThanEqualOrderByCreatedAtAsc(
                eq(OutboxEventStatus.PENDING), any(), any(PageRequest.class))).thenReturn(List.of(event));
        when(repository.findById(event.getId())).thenReturn(Optional.of(event));
        when(kafkaTemplate.send(eq(event.getEventType()), eq(event.getAggregateId().toString()), eq(event.getPayload())))
                .thenReturn(succeeded());

        publisher.publishPending();

        assertThat(event.getStatus()).isEqualTo(OutboxEventStatus.SENT);
        assertThat(event.getSentAt()).isNotNull();
        verify(repository).save(event);
        verifyNoMoreInteractions(kafkaTemplate);
    }

    @Test
    void publishPending_onFailureBelowMaxAttempts_schedulesRetryWithExponentialBackoff() {
        OutboxEvent event = pendingEvent();
        when(repository.findByStatusAndNextAttemptAtLessThanEqualOrderByCreatedAtAsc(
                eq(OutboxEventStatus.PENDING), any(), any(PageRequest.class))).thenReturn(List.of(event));
        when(repository.findById(event.getId())).thenReturn(Optional.of(event));
        when(kafkaTemplate.send(anyString(), anyString(), anyString())).thenReturn(failed());

        Instant before = Instant.now();
        publisher.publishPending();

        assertThat(event.getStatus()).isEqualTo(OutboxEventStatus.PENDING);
        assertThat(event.getAttempts()).isEqualTo(1);
        // base-backoff-seconds=2, attempt 1 -> 2 * 2^0 = 2s
        assertThat(event.getNextAttemptAt()).isAfter(before.plusSeconds(1));
        assertThat(event.getNextAttemptAt()).isBefore(before.plusSeconds(4));
        verify(repository).save(event);
        // Only the primary topic was attempted — attempts (1) has not yet reached maxAttempts (3),
        // so no DLQ publish should have been attempted.
        verify(kafkaTemplate, times(1)).send(anyString(), anyString(), anyString());
    }

    @Test
    void publishPending_onExhaustingMaxAttempts_marksFailedAndRoutesToDeadLetterTopic() {
        OutboxEvent event = pendingEvent();
        event.scheduleRetry(Instant.now()); // attempts=1
        event.scheduleRetry(Instant.now()); // attempts=2 — one more failure reaches maxAttempts=3
        when(repository.findByStatusAndNextAttemptAtLessThanEqualOrderByCreatedAtAsc(
                eq(OutboxEventStatus.PENDING), any(), any(PageRequest.class))).thenReturn(List.of(event));
        when(repository.findById(event.getId())).thenReturn(Optional.of(event));
        when(kafkaTemplate.send(eq(event.getEventType()), anyString(), anyString())).thenReturn(failed());
        when(kafkaTemplate.send(eq(event.getEventType() + ".DLQ"), anyString(), anyString())).thenReturn(succeeded());

        publisher.publishPending();

        assertThat(event.getStatus()).isEqualTo(OutboxEventStatus.FAILED);
        assertThat(event.getAttempts()).isEqualTo(3);
        verify(kafkaTemplate).send(eq(event.getEventType()), eq(event.getAggregateId().toString()), anyString());
        verify(kafkaTemplate).send(eq(event.getEventType() + ".DLQ"), eq(event.getAggregateId().toString()), anyString());
    }

    @Test
    void publishPending_whenDeadLetterPublishAlsoFails_stillLeavesEventTerminallyFailed() {
        OutboxEvent event = pendingEvent();
        event.scheduleRetry(Instant.now());
        event.scheduleRetry(Instant.now());
        when(repository.findByStatusAndNextAttemptAtLessThanEqualOrderByCreatedAtAsc(
                eq(OutboxEventStatus.PENDING), any(), any(PageRequest.class))).thenReturn(List.of(event));
        when(repository.findById(event.getId())).thenReturn(Optional.of(event));
        when(kafkaTemplate.send(anyString(), anyString(), anyString())).thenReturn(failed());

        publisher.publishPending();

        assertThat(event.getStatus()).isEqualTo(OutboxEventStatus.FAILED);
        assertThat(event.getAttempts()).isEqualTo(3);
    }
}
