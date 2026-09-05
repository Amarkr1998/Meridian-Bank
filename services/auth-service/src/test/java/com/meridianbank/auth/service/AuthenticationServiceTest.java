package com.meridianbank.auth.service;

import com.meridianbank.auth.config.SecurityProperties;
import com.meridianbank.auth.domain.User;
import com.meridianbank.auth.domain.UserRole;
import com.meridianbank.auth.exception.AccountLockedException;
import com.meridianbank.auth.exception.InvalidCredentialsException;
import com.meridianbank.auth.repository.LoginAttemptRepository;
import com.meridianbank.auth.repository.RefreshTokenRepository;
import com.meridianbank.auth.repository.UserRepository;
import com.meridianbank.auth.web.dto.LoginResponse;
import com.meridianbank.auth.web.dto.TokenResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthenticationServiceTest {

    private static final String RAW_PASSWORD = "Correct-Horse1!";

    @Mock private UserRepository userRepository;
    @Mock private RefreshTokenRepository refreshTokenRepository;
    @Mock private LoginAttemptRepository loginAttemptRepository;
    @Mock private JwtService jwtService;
    @Mock private MfaService mfaService;

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private AuthenticationService service;

    @BeforeEach
    void setUp() {
        SecurityProperties properties = new SecurityProperties(
                new SecurityProperties.Jwt("test-secret-at-least-32-bytes-long-xxxxx", 900, 604_800, "test"),
                new SecurityProperties.Mfa(300, 5, true),
                new SecurityProperties.Lockout(5, 900),
                new SecurityProperties.PasswordReset(1800, true));
        service = new AuthenticationService(userRepository, refreshTokenRepository, loginAttemptRepository,
                passwordEncoder, jwtService, mfaService, properties);
    }

    private User activeUser() {
        return new User("jane.doe@meridianbank.local", passwordEncoder.encode(RAW_PASSWORD), UserRole.CUSTOMER);
    }

    @Test
    void login_withUnknownEmail_throwsInvalidCredentials() {
        when(userRepository.findByEmail("nobody@meridianbank.local")).thenReturn(Optional.empty());

        assertThrows(InvalidCredentialsException.class,
                () -> service.login("nobody@meridianbank.local", RAW_PASSWORD, "127.0.0.1"));
        verify(loginAttemptRepository).save(argThat(a -> !a.isSuccessful() && a.getUserId() == null));
    }

    @Test
    void login_withWrongPassword_incrementsFailedAttemptsAndThrows() {
        User user = activeUser();
        when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));

        assertThrows(InvalidCredentialsException.class,
                () -> service.login(user.getEmail(), "totally-wrong", "127.0.0.1"));

        assertThat(user.getFailedLoginAttempts()).isEqualTo(1);
        assertThat(user.isLocked()).isFalse();
        verify(userRepository, atLeastOnce()).save(user);
    }

    @Test
    void login_locksAccountAfterConfiguredMaxFailedAttempts() {
        User user = activeUser();
        when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));

        for (int i = 0; i < 4; i++) {
            assertThrows(InvalidCredentialsException.class,
                    () -> service.login(user.getEmail(), "totally-wrong", "127.0.0.1"));
        }
        // 5th consecutive failure crosses the configured threshold (5) and locks the account.
        assertThrows(AccountLockedException.class,
                () -> service.login(user.getEmail(), "totally-wrong", "127.0.0.1"));
        assertThat(user.isLocked()).isTrue();

        // Even the correct password is rejected while locked.
        assertThrows(AccountLockedException.class,
                () -> service.login(user.getEmail(), RAW_PASSWORD, "127.0.0.1"));
    }

    @Test
    void login_withCorrectPasswordAndMfaEnabled_returnsChallengeNotTokens() {
        User user = activeUser();
        when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
        when(mfaService.createChallenge(user)).thenReturn(new MfaService.Challenge("challenge-1", "123456", 300));

        LoginResponse response = service.login(user.getEmail(), RAW_PASSWORD, "127.0.0.1");

        assertThat(response.mfaRequired()).isTrue();
        assertThat(response.mfaChallengeId()).isEqualTo("challenge-1");
        assertThat(response.tokens()).isNull();
        verify(jwtService, never()).generateAccessToken(any());
    }

    @Test
    void login_withMfaDisabled_issuesTokensDirectly() {
        User user = activeUser();
        user.setMfaEnabled(false);
        when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
        when(jwtService.generateAccessToken(user)).thenReturn("signed.jwt.token");
        when(jwtService.getAccessTokenTtlSeconds()).thenReturn(900L);
        when(jwtService.getRefreshTokenTtlSeconds()).thenReturn(604_800L);

        LoginResponse response = service.login(user.getEmail(), RAW_PASSWORD, "127.0.0.1");

        assertThat(response.mfaRequired()).isFalse();
        assertThat(response.tokens()).isNotNull();
        assertThat(response.tokens().accessToken()).isEqualTo("signed.jwt.token");
        verify(refreshTokenRepository).save(any());
        verify(loginAttemptRepository).save(argThat(a -> a.isSuccessful()));
    }

    @Test
    void verifyMfa_onSuccess_issuesTokens() {
        User user = activeUser();
        UUID userId = user.getId();
        when(mfaService.verify("challenge-1", "123456")).thenReturn(userId);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(jwtService.generateAccessToken(user)).thenReturn("signed.jwt.token");
        when(jwtService.getAccessTokenTtlSeconds()).thenReturn(900L);
        when(jwtService.getRefreshTokenTtlSeconds()).thenReturn(604_800L);

        TokenResponse tokens = service.verifyMfa("challenge-1", "123456", "127.0.0.1");

        assertThat(tokens.accessToken()).isEqualTo("signed.jwt.token");
        assertThat(tokens.userId()).isEqualTo(userId);
        assertThat(user.getLastLoginAt()).isNotNull();
        verify(loginAttemptRepository).save(argThat(a -> a.isSuccessful() && userId.equals(a.getUserId())));
    }
}
