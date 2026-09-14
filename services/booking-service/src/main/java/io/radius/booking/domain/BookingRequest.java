package io.radius.booking.domain;

import io.radius.common.web.ApiException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The request is the transaction. Everything before it is browsing.
 *
 * The legal transitions live here, next to the data they govern; the service
 * decides who may ask for one. Nothing outside {@code RequestService} calls
 * {@link #transitionTo}.
 */
@Entity
@Table(name = "booking_request")
public class BookingRequest {

    public enum Status { SENT, ACCEPTED, DECLINED, IN_PROGRESS, COMPLETED, CANCELLED, EXPIRED }

    /** The whole state machine, in one readable place. */
    private static final Map<Status, Set<Status>> ALLOWED = Map.of(
            Status.SENT, EnumSet.of(Status.ACCEPTED, Status.DECLINED, Status.CANCELLED, Status.EXPIRED),
            Status.ACCEPTED, EnumSet.of(Status.IN_PROGRESS, Status.CANCELLED, Status.COMPLETED),
            Status.IN_PROGRESS, EnumSet.of(Status.COMPLETED, Status.CANCELLED),
            Status.DECLINED, EnumSet.noneOf(Status.class),
            Status.COMPLETED, EnumSet.noneOf(Status.class),
            Status.CANCELLED, EnumSet.noneOf(Status.class),
            Status.EXPIRED, EnumSet.noneOf(Status.class));

    @Id
    private UUID id;

    @Column(name = "listing_id", nullable = false)
    private UUID listingId;

    @Column(name = "listing_title", nullable = false)
    private String listingTitle;

    @Column(name = "requester_id", nullable = false)
    private UUID requesterId;

    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Column(nullable = false)
    private int units;

    @Column(nullable = false)
    private String unit;

    @Column(name = "rate_minor", nullable = false)
    private long rateMinor;

    @Column(name = "amount_minor", nullable = false)
    private long amountMinor;

    @Column(name = "deposit_minor", nullable = false)
    private long depositMinor;

    @Column(name = "fee_minor", nullable = false)
    private long feeMinor;

    @Column(name = "total_minor", nullable = false)
    private long totalMinor;

    @Column(nullable = false)
    private String currency = "INR";

    private String message;

    @Column(nullable = false)
    private String status = Status.SENT.name();

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @Column(name = "accepted_at")
    private Instant acceptedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    protected BookingRequest() {}

    public BookingRequest(UUID listingId, String listingTitle, UUID requesterId, UUID ownerId,
                          LocalDate startDate, LocalDate endDate, int units, String unit,
                          long rateMinor, long amountMinor, long depositMinor, long feeMinor,
                          String currency, String message, Instant expiresAt) {
        this.id = UUID.randomUUID();
        this.listingId = listingId;
        this.listingTitle = listingTitle;
        this.requesterId = requesterId;
        this.ownerId = ownerId;
        this.startDate = startDate;
        this.endDate = endDate;
        this.units = units;
        this.unit = unit;
        this.rateMinor = rateMinor;
        this.amountMinor = amountMinor;
        this.depositMinor = depositMinor;
        this.feeMinor = feeMinor;
        this.totalMinor = amountMinor + depositMinor + feeMinor;
        this.currency = currency;
        this.message = message;
        this.expiresAt = expiresAt;
    }

    @PreUpdate
    void touch() { this.updatedAt = Instant.now(); }

    public Status status() { return Status.valueOf(status); }

    public boolean canTransitionTo(Status target) {
        return ALLOWED.getOrDefault(status(), Set.of()).contains(target);
    }

    /** Only {@code RequestService} calls this — controllers never set a status. */
    public void transitionTo(Status target) {
        if (!canTransitionTo(target)) {
            throw ApiException.conflict("illegal_transition",
                    "A request that is %s cannot become %s".formatted(status, target));
        }
        this.status = target.name();
        if (target == Status.ACCEPTED) this.acceptedAt = Instant.now();
        if (target == Status.COMPLETED) this.completedAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public boolean involves(UUID userId) {
        return requesterId.equals(userId) || ownerId.equals(userId);
    }

    public UUID counterpartOf(UUID userId) {
        return requesterId.equals(userId) ? ownerId : requesterId;
    }

    public UUID getId() { return id; }
    public UUID getListingId() { return listingId; }
    public String getListingTitle() { return listingTitle; }
    public UUID getRequesterId() { return requesterId; }
    public UUID getOwnerId() { return ownerId; }
    public LocalDate getStartDate() { return startDate; }
    public LocalDate getEndDate() { return endDate; }
    public int getUnits() { return units; }
    public String getUnit() { return unit; }
    public long getRateMinor() { return rateMinor; }
    public long getAmountMinor() { return amountMinor; }
    public long getDepositMinor() { return depositMinor; }
    public long getFeeMinor() { return feeMinor; }
    public long getTotalMinor() { return totalMinor; }
    public String getCurrency() { return currency; }
    public String getMessage() { return message; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getExpiresAt() { return expiresAt; }
}
