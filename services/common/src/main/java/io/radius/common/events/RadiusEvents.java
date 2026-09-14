package io.radius.common.events;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Every cross-service event in one file. They are records: additive changes are
 * safe, removals are not — that is what the {@code .v2} topic is for.
 *
 * Producers write these into their own outbox table in the same transaction as
 * the state change; a publisher drains the outbox to Kafka. No service ever
 * writes to Kafka and its database in two separate transactions.
 */
public final class RadiusEvents {

    // ---- user.v1 -----------------------------------------------------------
    public record UserRegistered(UUID userId, String phone, String displayName, Instant at) {}

    public record ProfileUpdated(UUID userId, String displayName, String photoUrl, String areaLabel,
                                 Double lat, Double lon, int searchRadiusKm, boolean openToRequests,
                                 List<String> tags, Instant at) {}

    /**
     * An ID or trade check was decided. Other services care because it changes
     * what a member may publish and what badge their card carries.
     */
    public record VerificationDecided(UUID userId, String kind, String state, boolean idChecked,
                                      Instant checkedAt, Instant at) {}

    /** A professional profile appeared, went live, or was suspended. */
    public record ProfessionalStatusChanged(UUID userId, String trade, String businessName,
                                            String state, Double shopLat, Double shopLon,
                                            int serviceRadiusKm, Instant at) {}

    // ---- listing.v1 --------------------------------------------------------
    public record ListingPublished(UUID listingId, UUID ownerId, String kind, String title,
                                   String description, Long priceMinor, String unit, Long depositMinor,
                                   Long buyPriceMinor, double lat, double lon, List<String> tags,
                                   String photoUrl, Instant at) {}

    public record ListingUpdated(UUID listingId, UUID ownerId, String kind, String title,
                                 String description, Long priceMinor, String unit, Long depositMinor,
                                 Long buyPriceMinor, double lat, double lon, List<String> tags,
                                 String photoUrl, String status, Instant at) {}

    public record ListingUnlisted(UUID listingId, UUID ownerId, String reason, Instant at) {}

    // ---- request.v1 --------------------------------------------------------
    public record RequestCreated(UUID requestId, UUID listingId, String listingTitle, UUID requesterId,
                                 UUID ownerId, LocalDate startDate, int units, String unit,
                                 long amountMinor, long depositMinor, Instant at) {}

    public record RequestAccepted(UUID requestId, UUID listingId, UUID requesterId, UUID ownerId,
                                  LocalDate startDate, LocalDate endDate, long amountMinor,
                                  long depositMinor, Instant at) {}

    public record RequestDeclined(UUID requestId, UUID listingId, UUID requesterId, UUID ownerId,
                                  String reason, Instant at) {}

    public record RequestCompleted(UUID requestId, UUID listingId, UUID requesterId, UUID ownerId,
                                   long amountMinor, long depositMinor, Instant at) {}

    public record RequestCancelled(UUID requestId, UUID listingId, UUID requesterId, UUID ownerId,
                                   UUID cancelledBy, Instant at) {}

    public record MessagePosted(UUID messageId, UUID requestId, UUID senderId, UUID recipientId,
                                String preview, Instant at) {}

    // ---- payment.v1 --------------------------------------------------------
    public record PaymentSettled(UUID paymentId, UUID requestId, UUID payerId, UUID payeeId,
                                 long amountMinor, long feeMinor, String state, Instant at) {}

    public record DepositReleased(UUID requestId, UUID payerId, long amountMinor, Instant at) {}

    // ---- notification.v1 ---------------------------------------------------
    /** Fan-in topic: anything that wants to reach a human publishes one of these. */
    public record NotificationRequested(UUID userId, String channelHint, String kind, String title,
                                        String body, String deepLink, Instant at) {}

    private RadiusEvents() {}
}
