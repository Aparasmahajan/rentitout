package io.radius.listing.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * A member who may not comment, until a date.
 *
 * A timestamp rather than a boolean, deliberately: an admin sets a duration and
 * the ban lifts itself. A boolean needs a second admin action that nobody
 * remembers to take, and the member is silently muted forever.
 */
@Entity
@Table(name = "comment_ban")
public class CommentBan {

    @Id
    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "banned_until", nullable = false)
    private Instant bannedUntil;

    @Column(nullable = false)
    private String reason;

    @Column(name = "set_by", nullable = false)
    private UUID setBy;

    @Column(name = "set_at", nullable = false)
    private Instant setAt = Instant.now();

    protected CommentBan() {}

    public CommentBan(UUID userId, Instant bannedUntil, String reason, UUID setBy) {
        this.userId = userId;
        this.bannedUntil = bannedUntil;
        this.reason = reason;
        this.setBy = setBy;
    }

    public UUID getUserId() { return userId; }
    public Instant getBannedUntil() { return bannedUntil; }
    public String getReason() { return reason; }
    public UUID getSetBy() { return setBy; }
    public Instant getSetAt() { return setAt; }

    /** A row that has expired is history, not a ban. */
    public boolean isActive() { return bannedUntil.isAfter(Instant.now()); }

    public void extend(Instant until, String reason, UUID by) {
        this.bannedUntil = until;
        this.reason = reason;
        this.setBy = by;
        this.setAt = Instant.now();
    }
}
