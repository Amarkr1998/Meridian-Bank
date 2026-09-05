package com.meridianbank.auth.domain;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

/**
 * Append-only record of every login attempt (success or failure), for lockout enforcement and
 * security investigation. Full cross-service audit trail arrives in Phase 11 (audit-service) —
 * this table is auth-service's local record in the meantime.
 */
@Entity
@Table(name = "login_attempts")
public class LoginAttempt {

    @Id
    private UUID id;

    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "email_attempted", nullable = false)
    private String emailAttempted;

    @Column(nullable = false)
    private boolean successful;

    @Column(name = "failure_reason")
    private String failureReason;

    @Column(name = "ip_address")
    private String ipAddress;

    @Column(name = "attempted_at", nullable = false)
    private Instant attemptedAt;

    protected LoginAttempt() {
    }

    public LoginAttempt(UUID userId, String emailAttempted, boolean successful,
                         String failureReason, String ipAddress) {
        this.id = UUID.randomUUID();
        this.userId = userId;
        this.emailAttempted = emailAttempted;
        this.successful = successful;
        this.failureReason = failureReason;
        this.ipAddress = ipAddress;
        this.attemptedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getEmailAttempted() {
        return emailAttempted;
    }

    public boolean isSuccessful() {
        return successful;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public Instant getAttemptedAt() {
        return attemptedAt;
    }
}
