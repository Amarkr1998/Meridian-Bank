package com.meridianbank.payment.outbox;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
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
 * The flagship proof for Phase 8: an {@link OutboxEvent} row written via {@link OutboxWriter}
 * genuinely reaches a real Kafka topic — not a mock — via {@link OutboxPublisher}'s scheduled
 * polling, and is marked SENT in the database once it does. See
 * docs/adr/0005-outbox-pattern.md and docs/architecture/kafka-architecture.md.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class OutboxPublisherIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Container
    @ServiceConnection
    static KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("apache/kafka:3.8.0"));

    // KafkaProducerConfig defines its own ProducerFactory/KafkaTemplate beans (see its Javadoc) —
    // reading spring.kafka.bootstrap-servers directly rather than via Spring Boot's
    // KafkaConnectionDetails autoconfiguration, so @ServiceConnection's usual auto-wiring for
    // Boot's own Kafka beans doesn't reach it. Point that property at the container explicitly.
    @DynamicPropertySource
    static void kafkaProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
    }

    @Autowired
    private OutboxWriter outboxWriter;

    @Autowired
    private OutboxEventRepository repository;

    private KafkaConsumer<String, String> consumer;

    @AfterEach
    void tearDown() {
        if (consumer != null) {
            consumer.close();
        }
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

    @Test
    void outboxRow_writtenByBusinessCode_isRelayedToTheRealTopicAndMarkedSent() {
        UUID aggregateId = UUID.randomUUID();
        consumer = subscribedConsumer("payment.completed");

        outboxWriter.write("payment.completed", "transaction", aggregateId,
                new TestPayload(aggregateId, "100.00"));

        // OutboxPublisher polls every meridian.outbox.poll-interval-ms (default 2s) — wait for it
        // to pick this row up and publish it for real, rather than calling publishPending()
        // directly, so this proves the actual scheduled wiring works end to end.
        ConsumerRecord<String, String> record = pollUntilRecordArrives(consumer, Duration.ofSeconds(15));

        assertThat(record.key()).isEqualTo(aggregateId.toString());
        assertThat(record.value()).contains("\"eventType\":\"payment.completed\"");
        assertThat(record.value()).contains(aggregateId.toString());
        assertThat(record.value()).contains("\"amount\":\"100.00\"");

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            OutboxEvent stored = repository.findAll().stream()
                    .filter(e -> e.getAggregateId().equals(aggregateId))
                    .findFirst().orElseThrow();
            assertThat(stored.getStatus()).isEqualTo(OutboxEventStatus.SENT);
            assertThat(stored.getSentAt()).isNotNull();
        });
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

    private record TestPayload(UUID transactionId, String amount) {
    }
}
