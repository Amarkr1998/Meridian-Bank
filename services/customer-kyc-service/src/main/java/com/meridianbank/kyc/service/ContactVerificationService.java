package com.meridianbank.kyc.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.meridianbank.kyc.config.ContactVerificationProperties;
import com.meridianbank.kyc.exception.ContactVerificationChallengeNotFoundException;
import com.meridianbank.kyc.exception.InvalidVerificationCodeException;
import com.meridianbank.kyc.exception.VerificationAttemptsExceededException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.UUID;

/**
 * Combined email/mobile verification simulation (one OTP represents both channels — a
 * deliberate simplification for this demo, see docs/architecture/onboarding-flow.md). Backed by
 * Redis, keyed by customer id: a new registration or resend overwrites any previous challenge, so
 * there is at most one active challenge per customer at a time.
 */
@Service
public class ContactVerificationService {

    private static final String KEY_PREFIX = "kyc:contact-verify:";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final ContactVerificationProperties properties;

    public ContactVerificationService(StringRedisTemplate redisTemplate, ObjectMapper objectMapper,
                                       ContactVerificationProperties properties) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    public record Challenge(String otp, long expiresInSeconds) {
    }

    public Challenge createChallenge(UUID customerId) {
        String otp = OtpHasher.generateOtp();
        long ttlSeconds = properties.otpTtlSeconds();
        store(customerId, new ChallengeData(OtpHasher.sha256Hex(otp), 0), Duration.ofSeconds(ttlSeconds));
        return new Challenge(otp, ttlSeconds);
    }

    public void verify(UUID customerId, String submittedOtp) {
        String key = KEY_PREFIX + customerId;
        String json = redisTemplate.opsForValue().get(key);
        if (json == null) {
            throw new ContactVerificationChallengeNotFoundException();
        }
        ChallengeData data = read(json);

        if (OtpHasher.sha256Hex(submittedOtp).equals(data.otpHash())) {
            redisTemplate.delete(key);
            return;
        }

        int attempts = data.attempts() + 1;
        if (attempts >= properties.maxAttempts()) {
            redisTemplate.delete(key);
            throw new VerificationAttemptsExceededException();
        }

        Long remainingTtl = redisTemplate.getExpire(key);
        Duration ttl = (remainingTtl != null && remainingTtl > 0)
                ? Duration.ofSeconds(remainingTtl)
                : Duration.ofSeconds(properties.otpTtlSeconds());
        store(customerId, new ChallengeData(data.otpHash(), attempts), ttl);
        throw new InvalidVerificationCodeException();
    }

    private void store(UUID customerId, ChallengeData data, Duration ttl) {
        try {
            redisTemplate.opsForValue().set(KEY_PREFIX + customerId, objectMapper.writeValueAsString(data), ttl);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to persist contact verification challenge", e);
        }
    }

    private ChallengeData read(String json) {
        try {
            return objectMapper.readValue(json, ChallengeData.class);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to read contact verification challenge", e);
        }
    }

    private record ChallengeData(String otpHash, int attempts) {
    }
}
