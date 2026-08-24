package io.radius.search.api;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class Dtos {

    /** Either a sentence in {@code q}, or the structured filters. Both hit one endpoint. */
    public record SearchRequest(
            @Size(max = 200) String q,
            @Size(max = 80) String keyword,
            @Size(max = 5) List<@Pattern(regexp = "RENT_ITEM|SELL_ITEM|TRADE_SERVICE|SKILL_FOR_HIRE|TEACHING|SPACE_OR_VEHICLE|OPEN_NEED") String> kinds,
            @Size(max = 10) List<String> tags,
            @Min(1) @Max(50) Integer radiusKm,
            LocalDate availableOn,
            @Pattern(regexp = "relevance|distance|price|rating") String sort,
            Double lat,
            Double lon,
            @Min(0) @Max(50) Integer page) {}

    /** What the parser understood — the client renders these as removable chips. */
    public record ParsedQuery(List<String> terms, List<String> tags, List<String> kinds,
                              Integer radiusKm, LocalDate day, String source) {}

    public record ListingHit(UUID id, String kind, String title, String description, Long priceMinor,
                             String unit, Long buyPriceMinor, String currency, String photoUrl,
                             double lat, double lon, double distanceKm, UUID ownerId, String ownerName,
                             String ownerPhoto, BigDecimal ownerRating, String areaLabel, double relevance) {}

    public record MemberHit(UUID id, String displayName, String photoUrl, String areaLabel, String bio,
                            BigDecimal rating, double distanceKm, List<String> tags) {}

    public record SearchResponse(ParsedQuery parsed, List<ListingHit> listings, List<MemberHit> members,
                                 int radiusKm, boolean hasMore) {}

    public record MapPin(UUID id, double lat, double lon, Long priceMinor, String kind, String title) {}

    public record MapCluster(double lat, double lon, int count) {}

    public record MapResponse(List<MapPin> pins, List<MapCluster> clusters, int total, boolean clustered) {}

    public record SaveSearchRequest(
            @Size(max = 60) String label,
            @NotBlank @Size(max = 200) String query,
            @Min(1) @Max(50) Integer radiusKm,
            @Pattern(regexp = "daily|weekly|off") String digestFrequency) {}

    public record SavedSearchDto(UUID id, String label, String query, int radiusKm,
                                 String digestFrequency, java.time.Instant createdAt) {}

    private Dtos() {}
}
