package io.radius.listing.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * One row for all seven kinds. A ladder to rent, a printer to sell, an
 * electrician for hire and an open need are the same shape with different
 * fields filled in — splitting them into seven tables buys nothing and costs
 * every query a union.
 */
@Entity
@Table(name = "listing")
public class Listing {

    public enum Kind {
        RENT_ITEM, SELL_ITEM, TRADE_SERVICE, SKILL_FOR_HIRE, TEACHING, SPACE_OR_VEHICLE, OPEN_NEED
    }

    public enum Unit { HOUR, DAY, WEEK, SESSION, ITEM }

    public enum Status { LIVE, PAUSED, UNLISTED }

    @Id
    private UUID id;

    @Column(name = "owner_id", nullable = false, updatable = false)
    private UUID ownerId;

    @Column(nullable = false)
    private String kind;

    @Column(nullable = false)
    private String title;

    private String description;

    @Column(name = "price_minor")
    private Long priceMinor;

    private String unit;

    @Column(name = "deposit_minor", nullable = false)
    private long depositMinor;

    @Column(name = "buy_price_minor")
    private Long buyPriceMinor;

    @Column(nullable = false)
    private String currency = "INR";

    @Column(nullable = false)
    private double lat;

    @Column(nullable = false)
    private double lon;

    /** True when fulfilling this listing means entering someone's home. */
    @Column(name = "home_visit", nullable = false)
    private boolean homeVisit;

    @Column(nullable = false)
    private String status = Status.LIVE.name();

    /**
     * Denormalised from listing_rating so a feed card renders without a join.
     * Null average until somebody rates — zero would claim a rating of zero,
     * which is not the same thing as "not yet rated".
     */
    @Column(name = "rating_avg")
    private BigDecimal ratingAvg;

    @Column(name = "rating_count", nullable = false)
    private int ratingCount;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected Listing() {}

    public Listing(UUID ownerId, Kind kind, String title, double lat, double lon) {
        this.id = UUID.randomUUID();
        this.ownerId = ownerId;
        this.kind = kind.name();
        this.title = title;
        this.lat = lat;
        this.lon = lon;
    }

    @PreUpdate
    void touch() { this.updatedAt = Instant.now(); }

    public UUID getId() { return id; }
    public UUID getOwnerId() { return ownerId; }
    public String getKind() { return kind; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public Long getPriceMinor() { return priceMinor; }
    public String getUnit() { return unit; }
    public long getDepositMinor() { return depositMinor; }
    public Long getBuyPriceMinor() { return buyPriceMinor; }
    public String getCurrency() { return currency; }
    public double getLat() { return lat; }
    public double getLon() { return lon; }
    public String getStatus() { return status; }
    public boolean isHomeVisit() { return homeVisit; }
    public Instant getCreatedAt() { return createdAt; }
    public BigDecimal getRatingAvg() { return ratingAvg; }
    public int getRatingCount() { return ratingCount; }

    public boolean isLive() { return Status.LIVE.name().equals(status); }
    public boolean isOwnedBy(UUID userId) { return ownerId.equals(userId); }

    public void setTitle(String title) { this.title = title; }
    public void setDescription(String description) { this.description = description; }
    public void setPriceMinor(Long priceMinor) { this.priceMinor = priceMinor; }
    public void setUnit(String unit) { this.unit = unit; }
    public void setDepositMinor(long depositMinor) { this.depositMinor = depositMinor; }
    public void setBuyPriceMinor(Long buyPriceMinor) { this.buyPriceMinor = buyPriceMinor; }
    public void setPoint(double lat, double lon) { this.lat = lat; this.lon = lon; }
    public void setStatus(Status status) { this.status = status.name(); }
    public void setHomeVisit(boolean homeVisit) { this.homeVisit = homeVisit; }

    /** Written only by RatingService, from a recomputed aggregate — never incremented in place. */
    public void setRating(BigDecimal average, int count) {
        this.ratingAvg = count == 0 ? null : average;
        this.ratingCount = count;
    }
}
