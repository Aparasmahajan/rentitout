package io.radius.listing.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * A 1–5 star rating with an optional title and body — the open product rating
 * people recognise, not the two-sided post-transaction review the build plan
 * describes for Phase 04. Both can exist; this is the one that makes a listing
 * page useful before anyone has transacted.
 *
 * {@code requestId} is what separates the two: set when the rater actually
 * completed a booking, and that is what earns the badge and the top sort slot.
 */
@Entity
@Table(name = "listing_rating")
public class ListingRating {

    @Id
    private UUID id = UUID.randomUUID();

    @Column(name = "listing_id", nullable = false)
    private UUID listingId;

    @Column(name = "author_id", nullable = false)
    private UUID authorId;

    @Column(nullable = false)
    private short stars;

    private String title;

    private String body;

    @Column(name = "request_id")
    private UUID requestId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "edited_at")
    private Instant editedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Column(name = "deleted_by")
    private UUID deletedBy;

    protected ListingRating() {}

    public ListingRating(UUID listingId, UUID authorId, short stars, String title, String body,
                         UUID requestId) {
        this.listingId = listingId;
        this.authorId = authorId;
        this.stars = stars;
        this.title = title;
        this.body = body;
        this.requestId = requestId;
    }

    public UUID getId() { return id; }
    public UUID getListingId() { return listingId; }
    public UUID getAuthorId() { return authorId; }
    public short getStars() { return stars; }
    public String getTitle() { return deletedAt == null ? title : null; }
    public String getBody() { return deletedAt == null ? body : null; }
    public UUID getRequestId() { return requestId; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getEditedAt() { return editedAt; }
    public boolean isDeleted() { return deletedAt != null; }
    public UUID getDeletedBy() { return deletedBy; }

    /** True when the author removed it themselves, rather than a moderator. */
    public boolean wasSelfRemoved() { return deletedAt != null && authorId.equals(deletedBy); }

    /** A rating tied to a completed booking. The badge the listing page shows. */
    public boolean isVerifiedBooking() { return requestId != null; }

    public void edit(short stars, String title, String body) {
        this.stars = stars;
        this.title = title;
        this.body = body;
        this.editedAt = Instant.now();
    }

    public void delete(UUID by) {
        if (deletedAt != null) return;
        this.deletedAt = Instant.now();
        this.deletedBy = by;
    }

    /**
     * Bring a rating the author removed themselves back with new content.
     *
     * The unique index allows one row per member per listing, so "I deleted
     * mine, now I want to rate again" has to reuse this row or it cannot happen
     * at all. Only ever called for a self-removal — a moderated rating stays
     * removed, or removal would just be a step in re-posting it.
     */
    public void restore(short stars, String title, String body, UUID requestId) {
        this.deletedAt = null;
        this.deletedBy = null;
        this.stars = stars;
        this.title = title;
        this.body = body;
        this.requestId = requestId;
        this.createdAt = Instant.now();
        this.editedAt = null;
    }
}
