package com.meridianbank.auth.domain;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "refresh_tokens")
public class RefreshToken {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    /** SHA-256 hash of the opaque refresh token — the raw token is never persisted. */
    @Column(name = "token_hash", nullable = false, unique = true)
    private String tokenHash;

    @Column(name = "issued_at", nullable = false)
    private Instant issuedAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(nullable = false)
    private boolean revoked = false;

    @Column(name = "replaced_by_id")
    private UUID replacedById;

    @Column(name = "created_by_ip")
    private String createdByIp;

    protected RefreshToken() {
    }

    public RefreshToken(UUID userId, String tokenHash, Instant expiresAt, String createdByIp) {
        this.id = UUID.randomUUID();
        this.userId = userId;
        this.tokenHash = tokenHash;
        this.issuedAt = Instant.now();
        this.expiresAt = expiresAt;
        this.createdByIp = createdByIp;
        this.revoked = false;
    }

    public boolean isActive() {
        return !revoked && expiresAt.isAfter(Instant.now());
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getTokenHash() {
        return tokenHash;
    }

    public Instant getIssuedAt() {
        return issuedAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public boolean isRevoked() {
        return revoked;
    }

    public void setRevoked(boolean revoked) {
        this.revoked = revoked;
    }

    public UUID getReplacedById() {
        return replacedById;
    }

    public void setReplacedById(UUID replacedById) {
        this.replacedById = replacedById;
    }

    public String getCreatedByIp() {
        return createdByIp;
    }
}
