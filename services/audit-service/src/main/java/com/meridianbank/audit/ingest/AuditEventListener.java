package com.meridianbank.audit.ingest;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Subscribes to {@code audit.event} under its own consumer group ({@code audit-service} — see
 * application.yml's {@code spring.kafka.consumer.group-id}), independent of any other service's
 * consumption of the same topic (there is none yet — see docs/kafka/topics.md). A thrown exception
 * here is caught by the container's {@link org.springframework.kafka.listener.DefaultErrorHandler}
 * (see AuditKafkaConfig), which retries with backoff and eventually routes to
 * {@code audit.event.DLQ} — this method itself does not catch or swallow anything.
 */
@Component
public class AuditEventListener {

    private static final Logger log = LoggerFactory.getLogger(AuditEventListener.class);

    private final AuditIngestService ingestService;

    public AuditEventListener(AuditIngestService ingestService) {
        this.ingestService = ingestService;
    }

    @KafkaListener(topics = "audit.event")
    public void onMessage(ConsumerRecord<String, String> record) {
        log.debug("Consuming audit.event at offset {} partition {}", record.offset(), record.partition());
        ingestService.ingest(record.value());
    }
}
