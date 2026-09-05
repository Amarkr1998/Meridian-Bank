package com.meridianbank.auth.service;

import com.meridianbank.auth.config.SecurityProperties;
import com.meridianbank.auth.domain.PasswordResetToken;
import com.meridianbank.auth.domain.User;
import com.meridianbank.auth.exception.InvalidPasswordResetTokenException;
import com.meridianbank.auth.repository.PasswordResetTokenRepository;
import com.meridianbank.auth.repository.RefreshTokenRepository;
import com.meridianbank.auth.repository.UserRepository;
import com.meridianbank.auth.web.dto.PasswordResetRequestedResponse;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Forgot-password workflow. The request side always returns the same response regardless of
 * whether the email matched an account, to avoid confirming account existence — see
 * {@link PasswordResetRequestedResponse} for the documented demo-mode exception to that rule
 * (there is no real email channel until notification-service, Phase 13).
 */
@Service
public class PasswordResetService {

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final SecurityProperties properties;

    public PasswordResetService(UserRepository userRepository,
                                 PasswordResetTokenRepository passwordResetTokenRepository,
                                 RefreshTokenRepository refreshTokenRepository,
                                 PasswordEncoder passwordEncoder,
                                 SecurityProperties properties) {
        this.userRepository = userRepository;
        this.passwordResetTokenRepository = passwordResetTokenRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.properties = properties;
    }

    @Transactional
    public PasswordResetRequestedResponse requestReset(String rawEmail) {
        String email = rawEmail.trim().toLowerCase();
        User user = userRepository.findByEmail(email).orElse(null);
        if (user == null) {
            return PasswordResetRequestedResponse.withoutToken();
        }

        String rawToken = TokenHasher.generateOpaqueToken();
        Instant expiresAt = Instant.now().plusSeconds(properties.passwordReset().tokenTtlSeconds());
        passwordResetTokenRepository.save(
                new PasswordResetToken(user.getId(), TokenHasher.sha256Hex(rawToken), expiresAt));

        return properties.passwordReset().demoExposeToken()
                ? PasswordResetRequestedResponse.withDevToken(rawToken)
                : PasswordResetRequestedResponse.withoutToken();
    }

    @Transactional
    public void confirmReset(String rawToken, String newPassword) {
        String hash = TokenHasher.sha256Hex(rawToken);
        PasswordResetToken resetToken = passwordResetTokenRepository.findByTokenHash(hash)
                .filter(PasswordResetToken::isValid)
                .orElseThrow(InvalidPasswordResetTokenException::new);

        User user = userRepository.findById(resetToken.getUserId())
                .orElseThrow(InvalidPasswordResetTokenException::new);

        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.setFailedLoginAttempts(0);
        user.setLockedUntil(null);
        userRepository.save(user);

        resetToken.setUsed(true);
        passwordResetTokenRepository.save(resetToken);

        // Force re-authentication on every device after a password change.
        refreshTokenRepository.revokeAllForUser(user.getId());
    }
}
