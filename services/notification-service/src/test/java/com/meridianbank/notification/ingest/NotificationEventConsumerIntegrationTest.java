package com.meridianbank.notification.ingest;

import com.meridianbank.notification.repository.NotificationRepository;
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
 * The flagship proof for Phase 13's consumer side: a raw message published to a real topic is
 * genuinely consumed by the running {@link NotificationEventListener} (not called directly) and
 * lands as a durable notification row; a redelivered message with the same {@code eventId} does
 * not duplicate it; and a structurally-broken message is retried and then genuinely routed to the
 * real DLQ topic — same pattern as audit-service's {@code AuditEventConsumerIntegrationTest}
 * (Phase 11), this project's first Kafka consumer.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "meridian.notification-consumer.max-attempts=2",
        "meridian.notification-consumer.base-backoff-ms=200",
        "meridian.notification-consumer.max-backoff-ms=200"
})
@Testcontainers
class NotificationEventConsumerIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Container
    @ServiceConnection
    static KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("apache/kafka:3.8.0"));

    @DynamicPropertySource
    static void kafkaProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
    }

    @Autowired
    private NotificationRepository repository;

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

    private String paymentCompletedEnvelope(UUID eventId, UUID customerId) {
        return """
                {
                  "eventId": "%s",
                  "eventType": "payment.completed",
                  "eventVersion": 1,
                  "occurredAt": "2026-09-04T12:00:00Z",
                  "correlationId": "corr-%s",
                  "producedBy": "payment-service",
                  "payload": {
                    "transactionId": "%s",
                    "customerId": "%s",
                    "sourceAccountId": "%s",
                    "destinationAccountId": "%s",
                    "amount": "40.00",
                    "currency": "USD"
                  }
                }
                """.formatted(eventId, eventId, UUID.randomUUID(), customerId, UUID.randomUUID(), UUID.randomUUID());
    }

    @Test
    void messagePublishedToRealTopic_isGenuinelyConsumedAndStored() {
        UUID eventId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();

        producer().send(new ProducerRecord<>("payment.completed", eventId.toString(),
                paymentCompletedEnvelope(eventId, customerId)));
        producer().flush();

        await().atMost(Duration.ofSeconds(40)).untilAsserted(() -> {
            var stored = repository.findAll().stream().filter(n -> n.getEventId().equals(eventId)).findFirst();
            assertThat(stored).isPresent();
            assertThat(stored.get().getCustomerId()).isEqualTo(customerId);
            assertThat(stored.get().getBody()).contains("40.00").contains("USD");
        });
    }

    @Test
    void redeliveredMessageWithSameEventId_doesNotDuplicateTheRow() {
        UUID eventId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        String message = paymentCompletedEnvelope(eventId, customerId);

        producer().send(new ProducerRecord<>("payment.completed", eventId.toString(), message));
        producer().flush();
        await().atMost(Duration.ofSeconds(40))
                .until(() -> repository.findAll().stream().anyMatch(n -> n.getEventId().equals(eventId)));

        producer().send(new ProducerRecord<>("payment.completed", eventId.toString(), message));
        producer().flush();

        await().pollDelay(Duration.ofSeconds(3)).atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            long count = repository.findAll().stream().filter(n -> n.getEventId().equals(eventId)).count();
            assertThat(count).isEqualTo(1);
        });
    }

    @Test
    void structurallyBrokenMessage_isRetriedThenRoutedToTheDlqTopic() {
        dlqConsumer = subscribedConsumer("payment.completed.DLQ");
        String poisonMessage = "{ this is not valid json";

        producer().send(new ProducerRecord<>("payment.completed", "poison-key", poisonMessage));
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
