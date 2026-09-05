package com.meridianbank.account.outbox;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Polls PENDING {@link OutboxEvent} rows and relays them to Kafka — the second half of the
 * transactional outbox pattern (see {@link OutboxWriter} and docs/adr/0005-outbox-pattern.md).
 *
 * <p>On a publish failure, the row is retried with exponential backoff (bounded by
 * {@code meridian.outbox.max-attempts}); once attempts are exhausted the row is marked FAILED
 * (terminal — so a permanently-broken event can never starve the rest of the queue) and the same
 * envelope is best-effort published to a {@code <eventType>.DLQ} topic so the failure is visible
 * and replayable rather than silently dropped — see docs/architecture/kafka-architecture.md
 * ("Retry + DLQ"). This is a producer-side retry/DLQ: this service implements no Kafka consumers
 * of its own. `audit-service` (Phase 11) is this project's first genuine consumer, and it does
 * consume the `audit.event` this service now also publishes for its sensitive actions (see its
 * `audit` package) — but this service's own domain events (`account.created`, `account.approved`)
 * still have no consumer; `notification-service` remains a future phase.
 *
 * <p>Single-instance assumption: rows are polled without a claim/lock step, which is safe with the
 * one replica per service this project runs locally, but would double-publish under horizontal
 * scale-out — a documented simplification, not a correctness gap for this project's scope.
 */
@Component
public class OutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);
    private static final long SEND_TIMEOUT_SECONDS = 5;

    private final OutboxEventRepository repository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final OutboxProperties properties;

    public OutboxPublisher(OutboxEventRepository repository, KafkaTemplate<String, String> kafkaTemplate,
                            OutboxProperties properties) {
        this.repository = repository;
        this.kafkaTemplate = kafkaTemplate;
        this.properties = properties;
    }

    @Scheduled(fixedDelayString = "${meridian.outbox.poll-interval-ms:2000}")
    public void publishPending() {
        List<OutboxEvent> batch = repository.findByStatusAndNextAttemptAtLessThanEqualOrderByCreatedAtAsc(
                OutboxEventStatus.PENDING, Instant.now(), PageRequest.of(0, properties.batchSize()));
        for (OutboxEvent event : batch) {
            publishOne(event);
        }
    }

    private void publishOne(OutboxEvent event) {
        try {
            kafkaTemplate.send(event.getEventType(), event.getAggregateId().toString(), event.getPayload())
                    .get(SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            markSent(event.getId());
        } catch (Exception e) {
            log.warn("Failed to publish outbox event {} ({}), attempt {}: {}", event.getId(), event.getEventType(),
                    event.getAttempts() + 1, e.getMessage());
            handleFailure(event.getId());
        }
    }

    private void markSent(UUID id) {
        repository.findById(id).ifPresent(event -> {
            event.markSent();
            repository.save(event);
        });
    }

    private void handleFailure(UUID id) {
        repository.findById(id).ifPresent(event -> {
            int attemptsAfter = event.getAttempts() + 1;
            if (attemptsAfter >= properties.maxAttempts()) {
                event.markFailed();
                repository.save(event);
                routeToDeadLetter(event);
            } else {
                long backoffSeconds = Math.min(
                        properties.baseBackoffSeconds() * (1L << (attemptsAfter - 1)),
                        properties.maxBackoffSeconds());
                event.scheduleRetry(Instant.now().plusSeconds(backoffSeconds));
                repository.save(event);
            }
        });
    }

    private void routeToDeadLetter(OutboxEvent event) {
        String dlqTopic = event.getEventType() + ".DLQ";
        try {
            kafkaTemplate.send(dlqTopic, event.getAggregateId().toString(), event.getPayload())
                    .get(SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            log.error("Outbox event {} ({}) exhausted {} attempts — routed to {}", event.getId(),
                    event.getEventType(), properties.maxAttempts(), dlqTopic);
        } catch (Exception e) {
            log.error("Outbox event {} ({}) exhausted {} attempts AND failed to route to {} — stuck in FAILED, "
                    + "requires manual investigation: {}", event.getId(), event.getEventType(),
                    properties.maxAttempts(), dlqTopic, e.getMessage());
        }
    }
}
