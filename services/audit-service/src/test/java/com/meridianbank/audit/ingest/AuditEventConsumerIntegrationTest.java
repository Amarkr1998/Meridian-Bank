package com.meridianbank.audit.ingest;

import com.meridianbank.audit.repository.AuditEventRepository;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.util.Collections;
import java.util.Properties;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * The flagship proof for Phase 11: this project's first genuine Kafka consumer, exercised against
 * a real broker end to end — a raw message published to the real {@code audit.event} topic is
 * genuinely consumed by the running {@link AuditEventListener} (not called directly) and lands as
 * a durable row; a redelivered message with the same {@code eventId} does not duplicate it; and a
 * structurally-broken message is retried and then genuinely routed to {@code audit.event.DLQ} — see
 * docs/adr/0012-audit-architecture.md and docs/architecture/kafka-architecture.md.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "meridian.audit-consumer.max-attempts=2",
        "meridian.audit-consumer.base-backoff-ms=200",
        "meridian.audit-consumer.max-backoff-ms=200"
})
@Testcontainers
class AuditEventConsumerIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Container
    @ServiceConnection
    static KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("apache/kafka:3.8.0"));

    // AuditKafkaConfig defines its own ConsumerFactory/ProducerFactory beans reading
    // spring.kafka.bootstrap-servers directly (see OutboxPublisherIntegrationTest's identical
    // note in payment-service) — @ServiceConnection's auto-wiring doesn't reach them.
    @DynamicPropertySource
    static void kafkaProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
    }

    @Autowired
    private AuditEventRepository repository;

    private KafkaProducer<String, String> producer;
    private KafkaConsumer<String, String> dlqConsumer;

    @AfterEach
    void tearDown() {
        if (producer != null) {
            producer.close();
        }
        if (dlqConsumer != null) {
            dlqConsumer.close();
        }
    }

    private KafkaProducer<String, String> producer() {
        if (producer == null) {
            Properties props = new Properties();
            props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers());
            props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
            props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
            producer = new KafkaProducer<>(props);
        }
        return producer;
    }

    private KafkaConsumer<String, String> subscribedConsumer(String topic) {
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers());
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "test-" + UUID.randomUUID());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        KafkaConsumer<String, String> c = new KafkaConsumer<>(props);
        c.subscribe(Collections.singletonList(topic));
        return c;
    }

    private String envelope(UUID eventId, String action, UUID actorId, UUID resourceId) {
        return """
                {
                  "eventId": "%s",
                  "eventType": "audit.event",
                  "eventVersion": 1,
                  "occurredAt": "2026-09-04T12:00:00Z",
                  "correlationId": "corr-%s",
                  "producedBy": "account-service",
                  "payload": {
                    "actorId": "%s",
                    "actorRole": "OPERATIONS",
                    "action": "%s",
                    "resourceType": "account",
                    "resourceId": "%s",
                    "result": "SUCCESS",
                    "detail": "integration test"
                  }
                }
                """.formatted(eventId, eventId, actorId, action, resourceId);
    }

    @Test
    void messagePublishedToRealTopic_isGenuinelyConsumedAndStored() {
        UUID eventId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        UUID resourceId = UUID.randomUUID();

        producer().send(new ProducerRecord<>("audit.event", eventId.toString(),
                envelope(eventId, "ACCOUNT_FROZEN", actorId, resourceId)));
        producer().flush();

        await().atMost(Duration.ofSeconds(40)).untilAsserted(() -> {
            var stored = repository.findAll().stream()
                    .filter(e -> e.getEventId().equals(eventId))
                    .findFirst();
            assertThat(stored).isPresent();
            assertThat(stored.get().getAction()).isEqualTo("ACCOUNT_FROZEN");
            assertThat(stored.get().getActorId()).isEqualTo(actorId);
            assertThat(stored.get().getResourceId()).isEqualTo(resourceId);
            assertThat(stored.get().getProducedBy()).isEqualTo("account-service");
        });
    }

    @Test
    void redeliveredMessageWithSameEventId_doesNotDuplicateTheRow() {
        UUID eventId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        UUID resourceId = UUID.randomUUID();
        String message = envelope(eventId, "ACCOUNT_FROZEN", actorId, resourceId);

        producer().send(new ProducerRecord<>("audit.event", eventId.toString(), message));
        producer().flush();
        await().atMost(Duration.ofSeconds(40))
                .until(() -> repository.findAll().stream().anyMatch(e -> e.getEventId().equals(eventId)));

        // Simulate an at-least-once redelivery of the exact same event.
        producer().send(new ProducerRecord<>("audit.event", eventId.toString(), message));
        producer().flush();

        // Give the (idempotent) redelivery a moment to be processed, then assert exactly one row.
        await().pollDelay(Duration.ofSeconds(3)).atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            long count = repository.findAll().stream().filter(e -> e.getEventId().equals(eventId)).count();
            assertThat(count).isEqualTo(1);
        });
    }

    @Test
    void structurallyBrokenMessage_isRetriedThenRoutedToTheDlqTopic() {
        dlqConsumer = subscribedConsumer("audit.event.DLQ");
        String poisonMessage = "{ this is not valid json";

        producer().send(new ProducerRecord<>("audit.event", "poison-key", poisonMessage));
        producer().flush();

        ConsumerRecord<String, String> dlqRecord = pollUntilRecordArrives(dlqConsumer, Duration.ofSeconds(40));
        assertThat(dlqRecord.value()).isEqualTo(poisonMessage);
    }

    private ConsumerRecord<String, String> pollUntilRecordArrives(KafkaConsumer<String, String> consumer,
                                                                    Duration timeout) {
        long deadline = System.currentTimeMillis() + timeout.toMillis();
        while (System.currentTimeMillis() < deadline) {
            ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(500));
            if (!records.isEmpty()) {
                return records.iterator().next();
            }
        }
        throw new AssertionError("No record arrived on the topic within " + timeout);
    }
}
