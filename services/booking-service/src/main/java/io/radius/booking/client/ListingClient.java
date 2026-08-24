package io.radius.booking.client;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.radius.common.web.ApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.LocalDate;
import java.util.UUID;

/**
 * booking-service must not read listing-service's tables, so it asks. The
 * endpoint is on the internal port and is never routed by the gateway.
 */
@Component
public class ListingClient {

    private static final Logger log = LoggerFactory.getLogger(ListingClient.class);

    public record ListingSnapshot(UUID id, UUID ownerId, String kind, String title, Long priceMinor,
                                  String unit, long depositMinor, Long buyPriceMinor, String currency,
                                  String status) {}

    private final RestClient http;

    public ListingClient(RestClient.Builder builder,
                         @org.springframework.beans.factory.annotation.Value("${radius.clients.listing-url}") String baseUrl) {
        this.http = builder.baseUrl(baseUrl).build();
    }

    /**
     * No fallback: pricing a request against a listing we cannot read would be
     * a guess, and a guess about money is worse than an error.
     */
    @CircuitBreaker(name = "listing")
    public ListingSnapshot snapshot(UUID listingId) {
        try {
            ListingSnapshot snapshot = http.get()
                    .uri("/internal/listings/{id}", listingId)
                    .retrieve()
                    .body(ListingSnapshot.class);
            if (snapshot == null) throw ApiException.notFound("Listing");
            return snapshot;
        } catch (org.springframework.web.client.HttpClientErrorException.NotFound e) {
            throw ApiException.notFound("Listing");
        }
    }

    /** A false here is advisory — the real guard is the overlap check in this service's own table. */
    @CircuitBreaker(name = "listing", fallbackMethod = "assumeFree")
    public boolean isFree(UUID listingId, LocalDate from, LocalDate to) {
        Boolean free = http.get()
                .uri(uri -> uri.path("/internal/listings/{id}/free")
                        .queryParam("from", from).queryParam("to", to).build(listingId))
                .retrieve()
                .body(Boolean.class);
        return Boolean.TRUE.equals(free);
    }

    @SuppressWarnings("unused")   // resilience4j resolves this by name
    private boolean assumeFree(UUID listingId, LocalDate from, LocalDate to, Throwable t) {
        log.warn("listing-service unavailable, falling back to our own calendar for {}", listingId, t);
        return true;
    }
}
