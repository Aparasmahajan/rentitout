package io.radius.listing.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * A comment on a listing, threaded exactly one level deep.
 *
 * Deletion is soft on purpose: a moderated comment keeps its row and loses its
 * body. A comment that disappears entirely also destroys the evidence, which is
 * the thing you want most when the same author is reported a second time.
 */
@Entity
@Table(name = "listing_comment")
public class ListingComment {

    @Id
    private UUID id = UUID.randomUUID();

    @Column(name = "listing_id", nullable = false)
    private UUID listingId;

    @Column(name = "author_id", nullable = false)
    private UUID authorId;

    /** Null for a top-level comment; the parent's id for a reply. Never a reply's id. */
    @Column(name = "parent_id")
    private UUID parentId;

    @Column(nullable = false)
    private String body;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "edited_at")
    private Instant editedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Column(name = "deleted_by")
    private UUID deletedBy;

    protected ListingComment() {}

    public ListingComment(UUID listingId, UUID authorId, UUID parentId, String body) {
        this.listingId = listingId;
        this.authorId = authorId;
        this.parentId = parentId;
        this.body = body;
    }

    public UUID getId() { return id; }
    public UUID getListingId() { return listingId; }
    public UUID getAuthorId() { return authorId; }
    public UUID getParentId() { return parentId; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getEditedAt() { return editedAt; }
    public Instant getDeletedAt() { return deletedAt; }
    public boolean isDeleted() { return deletedAt != null; }

    /** The body a reader may see. Callers never reach the raw column directly. */
    public String getBody() { return deletedAt == null ? body : null; }

    public void edit(String newBody) {
        this.body = newBody;
        this.editedAt = Instant.now();
    }

    /** Idempotent: deleting an already-deleted comment keeps the first decision. */
    public void delete(UUID by) {
        if (deletedAt != null) return;
        this.deletedAt = Instant.now();
        this.deletedBy = by;
    }
}
