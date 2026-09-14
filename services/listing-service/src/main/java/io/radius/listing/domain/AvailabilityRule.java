package io.radius.listing.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalTime;
import java.util.UUID;

/** The weekly pattern: "Saturdays, 9 to 12". Specific days that are taken are blocks. */
@Entity
@Table(name = "availability_rule")
public class AvailabilityRule {

    @Id
    private UUID id;

    @Column(name = "listing_id", nullable = false)
    private UUID listingId;

    /** ISO-8601: Monday is 1. */
    @Column(nullable = false)
    private int weekday;

    @Column(name = "from_time", nullable = false)
    private LocalTime fromTime;

    @Column(name = "to_time", nullable = false)
    private LocalTime toTime;

    protected AvailabilityRule() {}

    public AvailabilityRule(UUID listingId, int weekday, LocalTime fromTime, LocalTime toTime) {
        this.id = UUID.randomUUID();
        this.listingId = listingId;
        this.weekday = weekday;
        this.fromTime = fromTime;
        this.toTime = toTime;
    }

    public UUID getId() { return id; }
    public UUID getListingId() { return listingId; }
    public int getWeekday() { return weekday; }
    public LocalTime getFromTime() { return fromTime; }
    public LocalTime getToTime() { return toTime; }
}
