package com.meridianbank.fraud.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

import static com.meridianbank.fraud.security.CorrelationIdFilter.MDC_KEY;

/**
 * Called from inside an already-{@code @Transactional} business method so the outbox row commits
 * atomically with the business change it describes — see docs/adr/0005-outbox-pattern.md. Never
 * publishes to Kafka itself; {@link OutboxPublisher} relays PENDING rows separately.
 */
@Component
public class OutboxWriter {

    private static final String PRODUCED_BY = "fraud-risk-service";

    private final OutboxEventRepository repository;
    private final ObjectMapper objectMapper;

    public OutboxWriter(OutboxEventRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    public void write(String eventType, String aggregateType, UUID aggregateId, Object payload) {
        String correlationId = MDC.get(MDC_KEY);
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = UUID.randomUUID().toString();
        }
        EventEnvelope envelope = new EventEnvelope(UUID.randomUUID(), eventType, 1, Instant.now(),
                correlationId, PRODUCED_BY, payload);
        String json;
        try {
            json = objectMapper.writeValueAsString(envelope);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize outbox event envelope for " + eventType, e);
        }
        repository.save(new OutboxEvent(aggregateType, aggregateId, eventType, json));
    }
}
