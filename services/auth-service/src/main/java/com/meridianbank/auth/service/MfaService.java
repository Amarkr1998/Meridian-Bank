package com.meridianbank.auth.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.meridianbank.auth.config.SecurityProperties;
import com.meridianbank.auth.domain.User;
import com.meridianbank.auth.exception.InvalidMfaCodeException;
import com.meridianbank.auth.exception.MfaAttemptsExceededException;
import com.meridianbank.auth.exception.MfaChallengeNotFoundException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.UUID;

/**
 * MFA/OTP simulation backed by Redis (see docs/adr/0004-redis-for-idempotency-and-rate-limiting.md)
 * — a challenge is short-lived, one-time-use, and never falls back to the database. Only the OTP's
 * SHA-256 hash is ever stored, never the plaintext code.
 */
@Service
public class MfaService {

    private static final String KEY_PREFIX = "mfa:challenge:";
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final SecurityProperties properties;

    public MfaService(StringRedisTemplate redisTemplate, ObjectMapper objectMapper,
                       SecurityProperties properties) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    public record Challenge(String challengeId, String otp, long expiresInSeconds) {
    }

    public Challenge createChallenge(User user) {
        String otp = generateOtp();
        String challengeId = UUID.randomUUID().toString();
        long ttlSeconds = properties.mfa().otpTtlSeconds();
        ChallengeData data = new ChallengeData(user.getId(), TokenHasher.sha256Hex(otp), 0);
        store(challengeId, data, Duration.ofSeconds(ttlSeconds));
        return new Challenge(challengeId, otp, ttlSeconds);
    }

    /** @return the id of the user whose challenge was verified */
    public UUID verify(String challengeId, String submittedOtp) {
        String key = KEY_PREFIX + challengeId;
        String json = redisTemplate.opsForValue().get(key);
        if (json == null) {
            throw new MfaChallengeNotFoundException();
        }
        ChallengeData data = read(json);

        if (TokenHasher.sha256Hex(submittedOtp).equals(data.otpHash())) {
            redisTemplate.delete(key);
            return data.userId();
        }

        int attempts = data.attempts() + 1;
        if (attempts >= properties.mfa().maxAttempts()) {
            redisTemplate.delete(key);
            throw new MfaAttemptsExceededException();
        }

        Long remainingTtl = redisTemplate.getExpire(key);
        Duration ttl = (remainingTtl != null && remainingTtl > 0)
                ? Duration.ofSeconds(remainingTtl)
                : Duration.ofSeconds(properties.mfa().otpTtlSeconds());
        store(challengeId, new ChallengeData(data.userId(), data.otpHash(), attempts), ttl);
        throw new InvalidMfaCodeException();
    }

    private void store(String challengeId, ChallengeData data, Duration ttl) {
        try {
            redisTemplate.opsForValue().set(KEY_PREFIX + challengeId, objectMapper.writeValueAsString(data), ttl);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to persist MFA challenge", e);
        }
    }

    private ChallengeData read(String json) {
        try {
            return objectMapper.readValue(json, ChallengeData.class);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to read MFA challenge", e);
        }
    }

    private static String generateOtp() {
        int value = SECURE_RANDOM.nextInt(1_000_000);
        return String.format("%06d", value);
    }

    private record ChallengeData(UUID userId, String otpHash, int attempts) {
    }
}
