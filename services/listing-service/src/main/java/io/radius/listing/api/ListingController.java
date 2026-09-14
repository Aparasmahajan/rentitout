package io.radius.listing.api;

import io.radius.common.security.AuthUser;
import org.springframework.lang.Nullable;
import io.radius.listing.domain.Listing;
import io.radius.listing.service.ListingService;
import io.radius.listing.service.PhotoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@Tag(name = "Listings", description = "Create, edit and browse what neighbours offer")
public class ListingController {

    private final ListingService listings;
    private final PhotoService photoService;

    public ListingController(ListingService listings, PhotoService photoService) {
        this.listings = listings;
        this.photoService = photoService;
    }

    @PostMapping("/api/listings")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Publish a listing of any of the seven kinds")
    public Dtos.ListingResponse create(AuthUser me, @Valid @RequestBody Dtos.CreateListingRequest req) {
        return listings.create(me.id(), req);
    }

    @PatchMapping("/api/listings/{id}")
    @Operation(summary = "Edit a listing you own")
    public Dtos.ListingResponse update(AuthUser me, @PathVariable UUID id,
                                       @Valid @RequestBody Dtos.UpdateListingRequest req) {
        return listings.update(id, me.id(), req);
    }

    @GetMapping("/api/listings/mine")
    @Operation(summary = "The caller's own listings, live and paused")
    public List<Dtos.ListingCard> mine(AuthUser me) {
        return listings.mine(me.id());
    }

    @GetMapping("/api/listings/{id}")
    @Operation(summary = "One listing in full")
    public Dtos.ListingResponse detail(@Nullable AuthUser me, @PathVariable UUID id) {
        return listings.detail(id, me == null ? null : me.id());
    }

    @PostMapping("/api/listings/{id}/pause")
    @Operation(summary = "Take a listing out of the feed without deleting it")
    public Dtos.ListingResponse pause(AuthUser me, @PathVariable UUID id) {
        return listings.setStatus(id, me.id(), Listing.Status.PAUSED);
    }

    @PostMapping("/api/listings/{id}/resume")
    @Operation(summary = "Put a paused listing back in the feed")
    public Dtos.ListingResponse resume(AuthUser me, @PathVariable UUID id) {
        return listings.setStatus(id, me.id(), Listing.Status.LIVE);
    }

    @DeleteMapping("/api/listings/{id}")
    @Operation(summary = "Unlist for good — the row stays for the history of past requests")
    public Dtos.ListingResponse unlist(AuthUser me, @PathVariable UUID id) {
        return listings.setStatus(id, me.id(), Listing.Status.UNLISTED);
    }

    @GetMapping("/api/listings/{id}/availability")
    @Operation(summary = "The weekly pattern plus the days already taken")
    public Dtos.AvailabilityResponse availability(@PathVariable UUID id) {
        return listings.availability(id);
    }

    @GetMapping("/api/feed")
    @Operation(summary = "Everything live within the radius, closest first")
    public Dtos.FeedPage feed(@Nullable AuthUser me,
                              @RequestParam(required = false) Double lat,
                              @RequestParam(required = false) Double lon,
                              @RequestParam(required = false) Integer radiusKm,
                              @RequestParam(required = false) String kind,
                              @RequestParam(required = false) String cursor,
                              @RequestParam(required = false) Integer limit) {
        return listings.feed(me == null ? null : me.id(), lat, lon, radiusKm, kind, cursor, limit);
    }

    // ---- photos ------------------------------------------------------------

    @PostMapping("/api/photos/presign")
    @Operation(summary = "Get a pre-signed PUT so the client uploads straight to the object store")
    public Dtos.PresignResponse presign(AuthUser me, @Valid @RequestBody Dtos.PresignRequest req) {
        return photoService.presign(me.id(), req);
    }

    @PostMapping("/api/listings/{id}/photos")
    @Operation(summary = "Attach an uploaded object to a listing")
    public List<Dtos.PhotoDto> attach(AuthUser me, @PathVariable UUID id,
                                      @Valid @RequestBody Dtos.AttachPhotoRequest req) {
        return listings.attachPhoto(id, me.id(), req.objectKey(), photoService.publicUrl(req.objectKey()));
    }

    @DeleteMapping("/api/listings/{id}/photos/{photoId}")
    @Operation(summary = "Detach a photo")
    public ResponseEntity<Void> removePhoto(AuthUser me, @PathVariable UUID id, @PathVariable UUID photoId) {
        listings.removePhoto(id, me.id(), photoId);
        return ResponseEntity.noContent().build();
    }
}
