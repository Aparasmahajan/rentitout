package io.radius.user.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * Rotation: every refresh mints a new row and revokes the old one. If a revoked
 * token is presented again, the whole family is dropped — that is a replay.
 */
@Entity
@Table(name = "refresh_token")
public class RefreshToken {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "issued_at", nullable = false)
    private Instant issuedAt = Instant.now();

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "replaced_by")
    private UUID replacedBy;

    @Column(name = "user_agent")
    private String userAgent;

    protected RefreshToken() {}

    public RefreshToken(UUID userId, Instant expiresAt, String userAgent) {
        this.id = UUID.randomUUID();
        this.userId = userId;
        this.expiresAt = expiresAt;
        this.userAgent = userAgent;
    }

    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public Instant getExpiresAt() { return expiresAt; }
    public boolean isRevoked() { return revokedAt != null; }
    public boolean isExpired() { return Instant.now().isAfter(expiresAt); }
    public boolean isUsable() { return !isRevoked() && !isExpired(); }

    public void revoke(UUID replacedBy) {
        this.revokedAt = Instant.now();
        this.replacedBy = replacedBy;
    }
}
