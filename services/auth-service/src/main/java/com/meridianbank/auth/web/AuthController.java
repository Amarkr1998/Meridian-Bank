package com.meridianbank.auth.web;

import com.meridianbank.auth.domain.User;
import com.meridianbank.auth.repository.UserRepository;
import com.meridianbank.auth.service.AuthenticationService;
import com.meridianbank.auth.service.PasswordResetService;
import com.meridianbank.auth.service.UserProvisioningService;
import com.meridianbank.auth.web.dto.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthenticationService authenticationService;
    private final UserProvisioningService provisioningService;
    private final PasswordResetService passwordResetService;
    private final UserRepository userRepository;

    public AuthController(AuthenticationService authenticationService,
                           UserProvisioningService provisioningService,
                           PasswordResetService passwordResetService,
                           UserRepository userRepository) {
        this.authenticationService = authenticationService;
        this.provisioningService = provisioningService;
        this.passwordResetService = passwordResetService;
        this.userRepository = userRepository;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse register(@Valid @RequestBody RegisterRequest request) {
        User user = provisioningService.registerCustomer(request.email(), request.password());
        return UserResponse.from(user);
    }

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest) {
        return authenticationService.login(request.email(), request.password(), clientIp(httpRequest));
    }

    @PostMapping("/mfa/verify")
    public TokenResponse verifyMfa(@Valid @RequestBody MfaVerifyRequest request, HttpServletRequest httpRequest) {
        return authenticationService.verifyMfa(request.mfaChallengeId(), request.otp(), clientIp(httpRequest));
    }

    @PostMapping("/refresh")
    public TokenResponse refresh(@Valid @RequestBody RefreshRequest request, HttpServletRequest httpRequest) {
        return authenticationService.refresh(request.refreshToken(), clientIp(httpRequest));
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@Valid @RequestBody LogoutRequest request) {
        authenticationService.logout(request.refreshToken());
    }

    @PostMapping("/password-reset/request")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public PasswordResetRequestedResponse requestPasswordReset(@Valid @RequestBody PasswordResetRequest request) {
        return passwordResetService.requestReset(request.email());
    }

    @PostMapping("/password-reset/confirm")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void confirmPasswordReset(@Valid @RequestBody PasswordResetConfirmRequest request) {
        passwordResetService.confirmReset(request.token(), request.newPassword());
    }

    @GetMapping("/me")
    public MeResponse me(@AuthenticationPrincipal UUID userId) {
        User user = userRepository.findById(userId).orElseThrow(NoSuchElementException::new);
        return MeResponse.from(user);
    }

    @GetMapping("/sessions")
    public List<SessionResponse> sessions(@AuthenticationPrincipal UUID userId) {
        return authenticationService.listSessions(userId);
    }

    @DeleteMapping("/sessions/{sessionId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revokeSession(@AuthenticationPrincipal UUID userId, @PathVariable UUID sessionId) {
        authenticationService.revokeSession(userId, sessionId);
    }

    @DeleteMapping("/sessions")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revokeAllSessions(@AuthenticationPrincipal UUID userId) {
        authenticationService.revokeAllSessions(userId);
    }

    /** Internal staff provisioning — never reachable through self-service registration. */
    @PostMapping("/users")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public UserResponse createStaffUser(@Valid @RequestBody CreateStaffUserRequest request) {
        User user = provisioningService.createStaffUser(request.email(), request.password(), request.role());
        return UserResponse.from(user);
    }

    @GetMapping("/users")
    @PreAuthorize("hasRole('ADMIN')")
    public Page<UserResponse> listUsers(Pageable pageable) {
        return userRepository.findAll(pageable).map(UserResponse::from);
    }

    private String clientIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
