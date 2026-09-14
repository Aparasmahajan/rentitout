package io.radius.listing.api;

import io.radius.common.web.ApiException;
import io.radius.listing.domain.Listing;
import io.radius.listing.repo.ListingRepository;
import io.radius.listing.service.ListingService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Service-to-service only. These paths are not routed by the gateway, so they
 * are unreachable from outside the cluster network.
 *
 * booking-service needs the rate and the owner to price a request; it must not
 * read this service's tables to get them.
 */
@RestController
@RequestMapping("/internal/listings")
@Tag(name = "Internal", description = "Not exposed through the gateway")
public class InternalController {

    public record ListingSnapshot(UUID id, UUID ownerId, String kind, String title, Long priceMinor,
                                  String unit, long depositMinor, Long buyPriceMinor, String currency,
                                  String status) {}

    private final ListingRepository listings;
    private final ListingService service;

    public InternalController(ListingRepository listings, ListingService service) {
        this.listings = listings;
        this.service = service;
    }

    @GetMapping("/{id}")
    public ListingSnapshot snapshot(@PathVariable UUID id) {
        Listing l = listings.findById(id).orElseThrow(() -> ApiException.notFound("Listing"));
        return new ListingSnapshot(l.getId(), l.getOwnerId(), l.getKind(), l.getTitle(), l.getPriceMinor(),
                l.getUnit(), l.getDepositMinor(), l.getBuyPriceMinor(), l.getCurrency(), l.getStatus());
    }

    @GetMapping("/{id}/free")
    public boolean free(@PathVariable UUID id,
                        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return service.isFree(id, from, to);
    }
}
