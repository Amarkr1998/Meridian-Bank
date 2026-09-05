package com.meridianbank.account.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.meridianbank.account.config.BeneficiaryVerificationProperties;
import com.meridianbank.account.exception.BeneficiaryVerificationAttemptsExceededException;
import com.meridianbank.account.exception.BeneficiaryVerificationChallengeNotFoundException;
import com.meridianbank.account.exception.InvalidBeneficiaryVerificationCodeException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.UUID;

/**
 * OTP simulation confirming the account owner really intends to add this beneficiary — a control
 * ahead of high-value transfers per the project brief. Same pattern as
 * customer-kyc-service's ContactVerificationService: Redis-backed, keyed by beneficiary id, one
 * active challenge at a time, only the OTP's hash is ever stored.
 */
@Service
public class BeneficiaryVerificationService {

    private static final String KEY_PREFIX = "beneficiary:verify:";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final BeneficiaryVerificationProperties properties;

    public BeneficiaryVerificationService(StringRedisTemplate redisTemplate, ObjectMapper objectMapper,
                                           BeneficiaryVerificationProperties properties) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    public record Challenge(String otp, long expiresInSeconds) {
    }

    public Challenge createChallenge(UUID beneficiaryId) {
        String otp = OtpHasher.generateOtp();
        long ttlSeconds = properties.otpTtlSeconds();
        store(beneficiaryId, new ChallengeData(OtpHasher.sha256Hex(otp), 0), Duration.ofSeconds(ttlSeconds));
        return new Challenge(otp, ttlSeconds);
    }

    public void verify(UUID beneficiaryId, String submittedOtp) {
        String key = KEY_PREFIX + beneficiaryId;
        String json = redisTemplate.opsForValue().get(key);
        if (json == null) {
            throw new BeneficiaryVerificationChallengeNotFoundException();
        }
        ChallengeData data = read(json);

        if (OtpHasher.sha256Hex(submittedOtp).equals(data.otpHash())) {
            redisTemplate.delete(key);
            return;
        }

        int attempts = data.attempts() + 1;
        if (attempts >= properties.maxAttempts()) {
            redisTemplate.delete(key);
            throw new BeneficiaryVerificationAttemptsExceededException();
        }

        Long remainingTtl = redisTemplate.getExpire(key);
        Duration ttl = (remainingTtl != null && remainingTtl > 0)
                ? Duration.ofSeconds(remainingTtl)
                : Duration.ofSeconds(properties.otpTtlSeconds());
        store(beneficiaryId, new ChallengeData(data.otpHash(), attempts), ttl);
        throw new InvalidBeneficiaryVerificationCodeException();
    }

    private void store(UUID beneficiaryId, ChallengeData data, Duration ttl) {
        try {
            redisTemplate.opsForValue().set(KEY_PREFIX + beneficiaryId, objectMapper.writeValueAsString(data), ttl);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to persist beneficiary verification challenge", e);
        }
    }

    private ChallengeData read(String json) {
        try {
            return objectMapper.readValue(json, ChallengeData.class);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to read beneficiary verification challenge", e);
        }
    }

    private record ChallengeData(String otpHash, int attempts) {
    }
}
