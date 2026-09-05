package com.meridianbank.audit.config;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.*;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.ExponentialBackOffWithMaxRetries;

import java.util.HashMap;
import java.util.Map;

/**
 * This project's first genuine Kafka <em>consumer</em> — see
 * docs/architecture/kafka-architecture.md's "Idempotent consumers" / "Retry + DLQ" principles,
 * which describe exactly the policy wired here. A message that fails processing (see
 * {@link com.meridianbank.audit.ingest.AuditEventListener}) is retried with exponential backoff up
 * to {@code meridian.audit-consumer.max-attempts} total attempts; once exhausted, the raw record is
 * published as-is to {@code <topic>.DLQ} (e.g. {@code audit.event.DLQ}) by
 * {@link DeadLetterPublishingRecoverer} and the original offset is committed — a permanently
 * unprocessable message does not block the partition forever. This mirrors the producer-side
 * retry/DLQ policy every outbox-using service already implements (see any of their
 * {@code OutboxPublisher} classes) — same shape, opposite side of the topic.
 */
@Configuration
public class AuditKafkaConfig {

    @Bean
    public ConsumerFactory<String, String> consumerFactory(
            @Value("${spring.kafka.bootstrap-servers}") String bootstrapServers,
            @Value("${spring.kafka.consumer.group-id}") String groupId,
            @Value("${spring.kafka.consumer.auto-offset-reset}") String autoOffsetReset) {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, autoOffsetReset);
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        return new DefaultKafkaConsumerFactory<>(props);
    }

    /**
     * Producer used only by {@link DeadLetterPublishingRecoverer} to relay an exhausted message to
     * its {@code .DLQ} topic — this service otherwise never publishes anything (see
     * docs/adr/0012-audit-architecture.md, "insert and read only"). Same bounded timeouts as every
     * other service's outbound Kafka producer, so a broker outage fails a DLQ publish attempt fast
     * rather than stalling the listener container's error-handling thread.
     */
    @Bean
    public ProducerFactory<String, String> dlqProducerFactory(
            @Value("${spring.kafka.bootstrap-servers}") String bootstrapServers) {
        Map<String, Object> props = new HashMap<>();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.MAX_BLOCK_MS_CONFIG, 5_000);
        props.put(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG, 5_000);
        props.put(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, 10_000);
        return new DefaultKafkaProducerFactory<>(props);
    }

    @Bean
    public KafkaTemplate<String, String> dlqKafkaTemplate(ProducerFactory<String, String> dlqProducerFactory) {
        return new KafkaTemplate<>(dlqProducerFactory);
    }

    @Bean
    public DefaultErrorHandler errorHandler(KafkaTemplate<String, String> dlqKafkaTemplate,
                                             AuditConsumerProperties properties) {
        var recoverer = new DeadLetterPublishingRecoverer(dlqKafkaTemplate,
                (record, ex) -> new org.apache.kafka.common.TopicPartition(record.topic() + ".DLQ", -1));
        var backOff = new ExponentialBackOffWithMaxRetries(properties.maxAttempts() - 1);
        backOff.setInitialInterval(properties.baseBackoffMs());
        backOff.setMaxInterval(properties.maxBackoffMs());
        backOff.setMultiplier(2.0);
        return new DefaultErrorHandler(recoverer, backOff);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, String> kafkaListenerContainerFactory(
            ConsumerFactory<String, String> consumerFactory, DefaultErrorHandler errorHandler) {
        ConcurrentKafkaListenerContainerFactory<String, String> factory = new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);
        factory.setCommonErrorHandler(errorHandler);
        return factory;
    }
}
