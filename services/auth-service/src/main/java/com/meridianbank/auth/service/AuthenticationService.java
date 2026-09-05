package com.meridianbank.auth.service;

import com.meridianbank.auth.config.SecurityProperties;
import com.meridianbank.auth.domain.LoginAttempt;
import com.meridianbank.auth.domain.RefreshToken;
import com.meridianbank.auth.domain.User;
import com.meridianbank.auth.domain.UserStatus;
import com.meridianbank.auth.exception.*;
import com.meridianbank.auth.repository.LoginAttemptRepository;
import com.meridianbank.auth.repository.RefreshTokenRepository;
import com.meridianbank.auth.repository.UserRepository;
import com.meridianbank.auth.web.dto.LoginResponse;
import com.meridianbank.auth.web.dto.SessionResponse;
import com.meridianbank.auth.web.dto.TokenResponse;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Orchestrates login (password + lockout + MFA), token issuance/rotation, and session
 * management. See docs/security/security-architecture.md and
 * docs/architecture/onboarding-flow.md for how this fits the wider platform.
 */
@Service
public class AuthenticationService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final LoginAttemptRepository loginAttemptRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final MfaService mfaService;
    private final SecurityProperties properties;

    /** Pre-computed so a lookup of a non-existent email costs the same as a real one — mitigates
     *  timing-based user enumeration. */
    private final String dummyHash;

    public AuthenticationService(UserRepository userRepository,
                                  RefreshTokenRepository refreshTokenRepository,
                                  LoginAttemptRepository loginAttemptRepository,
                                  PasswordEncoder passwordEncoder,
                                  JwtService jwtService,
                                  MfaService mfaService,
                                  SecurityProperties properties) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.loginAttemptRepository = loginAttemptRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.mfaService = mfaService;
        this.properties = properties;
        this.dummyHash = passwordEncoder.encode("not-a-real-password-" + UUID.randomUUID());
    }

    @Transactional
    public LoginResponse login(String rawEmail, String rawPassword, String ipAddress) {
        String email = rawEmail.trim().toLowerCase();
        User user = userRepository.findByEmail(email).orElse(null);

        if (user == null) {
            passwordEncoder.matches(rawPassword, dummyHash);
            recordAttempt(null, email, false, "USER_NOT_FOUND", ipAddress);
            throw new InvalidCredentialsException();
        }
        if (user.isLocked()) {
            recordAttempt(user.getId(), email, false, "ACCOUNT_LOCKED", ipAddress);
            throw new AccountLockedException();
        }
        if (user.getStatus() != UserStatus.ACTIVE) {
            recordAttempt(user.getId(), email, false, "ACCOUNT_NOT_ACTIVE", ipAddress);
            throw new AccountNotActiveException();
        }

        if (!passwordEncoder.matches(rawPassword, user.getPasswordHash())) {
            applyFailedAttempt(user);
            recordAttempt(user.getId(), email, false, "INVALID_PASSWORD", ipAddress);
            if (user.isLocked()) {
                throw new AccountLockedException();
            }
            throw new InvalidCredentialsException();
        }

        user.setFailedLoginAttempts(0);
        user.setLockedUntil(null);

        if (user.isMfaEnabled()) {
            userRepository.save(user);
            MfaService.Challenge challenge = mfaService.createChallenge(user);
            String devOtp = properties.mfa().demoExposeOtp() ? challenge.otp() : null;
            return LoginResponse.mfaChallenge(challenge.challengeId(), challenge.expiresInSeconds(), devOtp);
        }

        user.setLastLoginAt(Instant.now());
        userRepository.save(user);
        recordAttempt(user.getId(), email, true, null, ipAddress);
        return LoginResponse.tokens(issueTokens(user, ipAddress).response());
    }

    @Transactional
    public TokenResponse verifyMfa(String challengeId, String otp, String ipAddress) {
        UUID userId = mfaService.verify(challengeId, otp);
        User user = userRepository.findById(userId)
                .orElseThrow(InvalidCredentialsException::new);
        user.setLastLoginAt(Instant.now());
        userRepository.save(user);
        recordAttempt(user.getId(), user.getEmail(), true, null, ipAddress);
        return issueTokens(user, ipAddress).response();
    }

    @Transactional
    public TokenResponse refresh(String rawRefreshToken, String ipAddress) {
        String hash = TokenHasher.sha256Hex(rawRefreshToken);
        RefreshToken existing = refreshTokenRepository.findByTokenHash(hash)
                .filter(RefreshToken::isActive)
                .orElseThrow(InvalidRefreshTokenException::new);

        User user = userRepository.findById(existing.getUserId())
                .orElseThrow(InvalidRefreshTokenException::new);
        if (user.getStatus() != UserStatus.ACTIVE || user.isLocked()) {
            throw new InvalidRefreshTokenException();
        }

        existing.setRevoked(true);
        IssuedTokens issued = issueTokens(user, ipAddress);
        existing.setReplacedById(issued.refreshTokenId());
        refreshTokenRepository.save(existing);
        return issued.response();
    }

    @Transactional
    public void logout(String rawRefreshToken) {
        String hash = TokenHasher.sha256Hex(rawRefreshToken);
        refreshTokenRepository.findByTokenHash(hash).ifPresent(token -> {
            token.setRevoked(true);
            refreshTokenRepository.save(token);
        });
    }

    @Transactional(readOnly = true)
    public List<SessionResponse> listSessions(UUID userId) {
        return refreshTokenRepository.findByUserIdAndRevokedFalse(userId).stream()
                .filter(RefreshToken::isActive)
                .map(SessionResponse::from)
                .toList();
    }

    @Transactional
    public void revokeSession(UUID userId, UUID sessionId) {
        RefreshToken token = refreshTokenRepository.findById(sessionId)
                .filter(t -> t.getUserId().equals(userId))
                .orElseThrow(SessionNotFoundException::new);
        token.setRevoked(true);
        refreshTokenRepository.save(token);
    }

    @Transactional
    public void revokeAllSessions(UUID userId) {
        refreshTokenRepository.revokeAllForUser(userId);
    }

    private IssuedTokens issueTokens(User user, String ipAddress) {
        String accessToken = jwtService.generateAccessToken(user);
        String rawRefreshToken = TokenHasher.generateOpaqueToken();
        Instant expiresAt = Instant.now().plusSeconds(jwtService.getRefreshTokenTtlSeconds());
        RefreshToken entity = new RefreshToken(user.getId(), TokenHasher.sha256Hex(rawRefreshToken),
                expiresAt, ipAddress);
        refreshTokenRepository.save(entity);
        TokenResponse response = TokenResponse.bearer(accessToken, rawRefreshToken,
                jwtService.getAccessTokenTtlSeconds(), user.getId(), user.getEmail(), user.getRole());
        return new IssuedTokens(response, entity.getId());
    }

    private void applyFailedAttempt(User user) {
        int attempts = user.getFailedLoginAttempts() + 1;
        user.setFailedLoginAttempts(attempts);
        if (attempts >= properties.lockout().maxFailedAttempts()) {
            user.setLockedUntil(Instant.now().plusSeconds(properties.lockout().lockoutDurationSeconds()));
            user.setFailedLoginAttempts(0);
        }
        userRepository.save(user);
    }

    private void recordAttempt(UUID userId, String email, boolean successful, String reason, String ip) {
        loginAttemptRepository.save(new LoginAttempt(userId, email, successful, reason, ip));
    }

    private record IssuedTokens(TokenResponse response, UUID refreshTokenId) {
    }
}
