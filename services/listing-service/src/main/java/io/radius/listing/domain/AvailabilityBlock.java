package io.radius.listing.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Days that are not available. Written by the owner by hand, or by the
 * RequestAccepted consumer — in which case it carries the request id, and the
 * unique index on that column is what makes a redelivered event a no-op.
 */
@Entity
@Table(name = "availability_block")
public class AvailabilityBlock {

    @Id
    private UUID id;

    @Column(name = "listing_id", nullable = false)
    private UUID listingId;

    @Column(name = "date_from", nullable = false)
    private LocalDate dateFrom;

    @Column(name = "date_to", nullable = false)
    private LocalDate dateTo;

    @Column(nullable = false)
    private String reason = "manual";

    @Column(name = "request_id")
    private UUID requestId;

    protected AvailabilityBlock() {}

    public AvailabilityBlock(UUID listingId, LocalDate from, LocalDate to, String reason, UUID requestId) {
        this.id = UUID.randomUUID();
        this.listingId = listingId;
        this.dateFrom = from;
        this.dateTo = to;
        this.reason = reason;
        this.requestId = requestId;
    }

    public UUID getId() { return id; }
    public UUID getListingId() { return listingId; }
    public LocalDate getDateFrom() { return dateFrom; }
    public LocalDate getDateTo() { return dateTo; }
    public String getReason() { return reason; }
    public UUID getRequestId() { return requestId; }
}
