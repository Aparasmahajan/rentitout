package io.radius.listing.api;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

public final class Dtos {

    public record AvailabilityRuleDto(
            @Min(1) @Max(7) int weekday,
            @NotNull LocalTime from,
            @NotNull LocalTime to) {}

    public record CreateListingRequest(
            @NotBlank @Pattern(regexp = "RENT_ITEM|SELL_ITEM|TRADE_SERVICE|SKILL_FOR_HIRE|TEACHING|SPACE_OR_VEHICLE|OPEN_NEED")
            String kind,
            @NotBlank @Size(max = 120) String title,
            @Size(max = 4000) String description,
            @PositiveOrZero Long priceMinor,
            @Pattern(regexp = "HOUR|DAY|WEEK|SESSION|ITEM") String unit,
            @PositiveOrZero Long depositMinor,
            @PositiveOrZero Long buyPriceMinor,
            @NotNull @DecimalMin("-90.0") @DecimalMax("90.0") Double lat,
            @NotNull @DecimalMin("-180.0") @DecimalMax("180.0") Double lon,
            @Size(max = 10) List<@NotBlank String> tags,
            List<AvailabilityRuleDto> availability,
            /** True when doing this job means entering the customer's home. */
            Boolean homeVisit) {}

    public record UpdateListingRequest(
            @Size(max = 120) String title,
            @Size(max = 4000) String description,
            @PositiveOrZero Long priceMinor,
            @Pattern(regexp = "HOUR|DAY|WEEK|SESSION|ITEM") String unit,
            @PositiveOrZero Long depositMinor,
            @PositiveOrZero Long buyPriceMinor,
            @DecimalMin("-90.0") @DecimalMax("90.0") Double lat,
            @DecimalMin("-180.0") @DecimalMax("180.0") Double lon,
            @Size(max = 10) List<@NotBlank String> tags,
            List<AvailabilityRuleDto> availability,
            Boolean homeVisit) {}

    public record PhotoDto(UUID id, String url, int sortOrder) {}

    public record OwnerDto(UUID id, String displayName, String photoUrl, String areaLabel,
                           boolean idChecked, boolean professional, String trade) {}

    public record ListingResponse(UUID id, String kind, String title, String description,
                                  Long priceMinor, String unit, Long depositMinor, Long buyPriceMinor,
                                  String currency, double lat, double lon, String status,
                                  List<String> tags, List<PhotoDto> photos,
                                  List<AvailabilityRuleDto> availability, OwnerDto owner,
                                  Double distanceKm, boolean mine, boolean homeVisit,
                                  /** Null until somebody rates — not zero, which would read as a rating of zero. */
                                  java.math.BigDecimal ratingAvg, int ratingCount) {}

    /** The card shape the feed, search results and dashboards all render. */
    public record ListingCard(UUID id, String kind, String title, Long priceMinor, String unit,
                              Long buyPriceMinor, String currency, String photoUrl, double lat, double lon,
                              double distanceKm, OwnerDto owner, String status, boolean homeVisit,
                              java.math.BigDecimal ratingAvg, int ratingCount) {}

    public record FeedPage(List<ListingCard> items, String nextCursor, int radiusKm) {}

    public record PresignRequest(
            @NotBlank @Pattern(regexp = "image/jpeg|image/png|image/webp") String contentType,
            @Min(1) @Max(10_485_760) long sizeBytes) {}

    public record PresignResponse(String uploadUrl, String objectKey, String publicUrl, int expiresInSeconds) {}

    public record AttachPhotoRequest(@NotBlank String objectKey) {}

    public record BlockedRange(LocalDate from, LocalDate to, String reason) {}

    public record AvailabilityResponse(UUID listingId, List<AvailabilityRuleDto> weekly,
                                       List<BlockedRange> blocked) {}

    private Dtos() {}
}
