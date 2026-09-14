package io.radius.listing.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * "This member finished a booking on this listing" — a projection of
 * RequestCompleted, kept locally so a rating can be badged verified without a
 * synchronous call to booking-service.
 *
 * The request id is the primary key, which makes the Kafka consumer idempotent
 * by construction: a redelivered event writes the same row.
 */
@Entity
@Table(name = "completed_booking")
public class CompletedBooking {

    @Id
    @Column(name = "request_id")
    private UUID requestId;

    @Column(name = "listing_id", nullable = false)
    private UUID listingId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "completed_at", nullable = false)
    private Instant completedAt = Instant.now();

    protected CompletedBooking() {}

    public CompletedBooking(UUID requestId, UUID listingId, UUID userId, Instant completedAt) {
        this.requestId = requestId;
        this.listingId = listingId;
        this.userId = userId;
        if (completedAt != null) this.completedAt = completedAt;
    }

    public UUID getRequestId() { return requestId; }
    public UUID getListingId() { return listingId; }
    public UUID getUserId() { return userId; }
    public Instant getCompletedAt() { return completedAt; }
}
