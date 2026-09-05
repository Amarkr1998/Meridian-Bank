package com.meridianbank.payment.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.meridianbank.payment.config.IdempotencyProperties;
import com.meridianbank.payment.exception.IdempotencyKeyReuseException;
import com.meridianbank.payment.exception.PaymentInProgressException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.UUID;

/**
 * Redis-backed idempotency claim, keyed by (customer, Idempotency-Key) — see
 * docs/adr/0006-idempotency.md. A key is claimed atomically (SET NX) before any validation runs;
 * a repeat of the same key+payload short-circuits to the original result, a repeat with a
 * different payload is rejected as a client bug, and a repeat while the first attempt is still
 * mid-flight is rejected rather than double-processed. The database's own unique constraint on
 * (customer_id, idempotency_key) — see V1__init_payment_schema.sql — is the durable fallback if
 * this Redis state is ever lost mid-flight.
 */
@Service
public class IdempotencyService {

    private static final String KEY_PREFIX = "payment:idempotency:";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final IdempotencyProperties properties;

    public IdempotencyService(StringRedisTemplate redisTemplate, ObjectMapper objectMapper,
                               IdempotencyProperties properties) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    public enum Outcome { CLAIMED, REPLAY }

    public record ClaimResult(Outcome outcome, UUID replayTransactionId) {
    }

    /**
     * @throws IdempotencyKeyReuseException if the key was already used with a different payload
     * @throws PaymentInProgressException   if a request with this key is currently mid-flight
     */
    public ClaimResult claim(UUID customerId, String idempotencyKey, String requestFingerprint) {
        String key = redisKey(customerId, idempotencyKey);
        String inProgressJson = write(new Entry("IN_PROGRESS", requestFingerprint, null));

        Boolean claimed = redisTemplate.opsForValue()
                .setIfAbsent(key, inProgressJson, Duration.ofSeconds(properties.inProgressTtlSeconds()));
        if (Boolean.TRUE.equals(claimed)) {
            return new ClaimResult(Outcome.CLAIMED, null);
        }

        String existingJson = redisTemplate.opsForValue().get(key);
        if (existingJson == null) {
            // The prior claim's TTL lapsed between our failed SETNX and this read — safe to
            // reclaim; a genuinely concurrent claim would still be caught by the DB unique
            // constraint if both somehow proceeded.
            Boolean reclaimed = redisTemplate.opsForValue()
                    .setIfAbsent(key, inProgressJson, Duration.ofSeconds(properties.inProgressTtlSeconds()));
            if (Boolean.TRUE.equals(reclaimed)) {
                return new ClaimResult(Outcome.CLAIMED, null);
            }
            throw new PaymentInProgressException();
        }

        Entry existing = read(existingJson);
        if (!existing.requestFingerprint().equals(requestFingerprint)) {
            throw new IdempotencyKeyReuseException();
        }
        if ("IN_PROGRESS".equals(existing.status())) {
            throw new PaymentInProgressException();
        }
        return new ClaimResult(Outcome.REPLAY, UUID.fromString(existing.transactionId()));
    }

    public void complete(UUID customerId, String idempotencyKey, String requestFingerprint, UUID transactionId) {
        String key = redisKey(customerId, idempotencyKey);
        String json = write(new Entry("COMPLETED", requestFingerprint, transactionId.toString()));
        redisTemplate.opsForValue().set(key, json, Duration.ofSeconds(properties.completedTtlSeconds()));
    }

    private String redisKey(UUID customerId, String idempotencyKey) {
        return KEY_PREFIX + customerId + ":" + idempotencyKey;
    }

    private String write(Entry entry) {
        try {
            return objectMapper.writeValueAsString(entry);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize idempotency entry", e);
        }
    }

    private Entry read(String json) {
        try {
            return objectMapper.readValue(json, Entry.class);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to read idempotency entry", e);
        }
    }

    private record Entry(String status, String requestFingerprint, String transactionId) {
    }
}
