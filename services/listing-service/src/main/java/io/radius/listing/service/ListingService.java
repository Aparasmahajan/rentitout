package io.radius.listing.service;

import io.radius.common.events.RadiusEvents;
import io.radius.common.events.Topics;
import io.radius.common.outbox.OutboxService;
import io.radius.common.support.Cursor;
import io.radius.common.support.Geo;
import io.radius.common.web.ApiException;
import io.radius.listing.api.Dtos;
import io.radius.listing.domain.Listing;
import io.radius.listing.domain.AvailabilityBlock;
import io.radius.listing.domain.AvailabilityRule;
import io.radius.listing.domain.CompletedBooking;
import io.radius.listing.domain.ListingPhoto;
import io.radius.listing.domain.ListingTag;
import io.radius.listing.domain.MemberLocation;
import io.radius.listing.repo.CompletedBookingRepository;
import io.radius.listing.repo.FeedQuery;
import io.radius.listing.repo.AvailabilityBlockRepository;
import io.radius.listing.repo.AvailabilityRuleRepository;
import io.radius.listing.repo.ListingPhotoRepository;
import io.radius.listing.repo.ListingRepository;
import io.radius.listing.repo.ListingTagRepository;
import io.radius.listing.repo.MemberLocationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class ListingService {

    private static final int MAX_LIVE_LISTINGS = 50;
    private static final int MAX_PHOTOS = 8;
    private static final int DEFAULT_PAGE = 20;
    /** Guests see feed points snapped to this grid; members see the usual ~100 m. */
    private static final double GUEST_PRECISION_M = 250;

    private final ListingRepository listings;
    private final ListingPhotoRepository photos;
    private final ListingTagRepository tags;
    private final AvailabilityRuleRepository rules;
    private final AvailabilityBlockRepository blocks;
    private final MemberLocationRepository members;
    private final CompletedBookingRepository completedBookings;
    private final FeedQuery feedQuery;
    private final OutboxService outbox;

    public ListingService(ListingRepository listings, ListingPhotoRepository photos,
                          ListingTagRepository tags, AvailabilityRuleRepository rules,
                          AvailabilityBlockRepository blocks, MemberLocationRepository members,
                          CompletedBookingRepository completedBookings,
                          FeedQuery feedQuery, OutboxService outbox) {
        this.listings = listings;
        this.photos = photos;
        this.tags = tags;
        this.rules = rules;
        this.blocks = blocks;
        this.members = members;
        this.completedBookings = completedBookings;
        this.feedQuery = feedQuery;
        this.outbox = outbox;
    }

    // ---- write side --------------------------------------------------------

    @Transactional
    public Dtos.ListingResponse create(UUID ownerId, Dtos.CreateListingRequest req) {
        long live = listings.findByOwnerIdOrderByCreatedAtDesc(ownerId).stream()
                .filter(Listing::isLive).count();
        if (live >= MAX_LIVE_LISTINGS) {
            throw ApiException.conflict("too_many_listings",
                    "You already have " + MAX_LIVE_LISTINGS + " live listings");
        }

        Listing.Kind kind = Listing.Kind.valueOf(req.kind());
        requirePrice(kind, req.priceMinor(), req.buyPriceMinor());

        boolean homeVisit = Boolean.TRUE.equals(req.homeVisit());
        if (homeVisit) requireCheckedProfessional(ownerId);

        Listing listing = new Listing(ownerId, kind, req.title().trim(), req.lat(), req.lon());
        listing.setDescription(req.description());
        listing.setPriceMinor(req.priceMinor());
        listing.setUnit(req.unit());
        listing.setDepositMinor(req.depositMinor() == null ? 0 : req.depositMinor());
        listing.setBuyPriceMinor(req.buyPriceMinor());
        listing.setHomeVisit(homeVisit);
        listings.save(listing);

        replaceTags(listing.getId(), req.tags());
        replaceAvailability(listing.getId(), req.availability());
        publishPublished(listing, req.tags());

        return detail(listing.getId(), ownerId);
    }

    @Transactional
    public Dtos.ListingResponse update(UUID listingId, UUID callerId, Dtos.UpdateListingRequest req) {
        Listing listing = owned(listingId, callerId);

        if (req.title() != null && !req.title().isBlank()) listing.setTitle(req.title().trim());
        if (req.description() != null) listing.setDescription(req.description());
        if (req.priceMinor() != null) listing.setPriceMinor(req.priceMinor());
        if (req.unit() != null) listing.setUnit(req.unit());
        if (req.depositMinor() != null) listing.setDepositMinor(req.depositMinor());
        if (req.buyPriceMinor() != null) listing.setBuyPriceMinor(req.buyPriceMinor());

        if ((req.lat() == null) != (req.lon() == null)) {
            throw ApiException.badRequest("bad_point", "Send lat and lon together");
        }
        if (req.lat() != null) listing.setPoint(req.lat(), req.lon());
        if (req.homeVisit() != null) {
            if (req.homeVisit()) requireCheckedProfessional(callerId);
            listing.setHomeVisit(req.homeVisit());
        }

        listings.save(listing);
        if (req.tags() != null) replaceTags(listingId, req.tags());
        if (req.availability() != null) replaceAvailability(listingId, req.availability());

        publishUpdated(listing);
        return detail(listingId, callerId);
    }

    @Transactional
    public Dtos.ListingResponse setStatus(UUID listingId, UUID callerId, Listing.Status status) {
        Listing listing = owned(listingId, callerId);
        listing.setStatus(status);
        listings.save(listing);

        if (status == Listing.Status.UNLISTED) {
            outbox.publish(Topics.LISTING, listing.getId(), new RadiusEvents.ListingUnlisted(
                    listing.getId(), listing.getOwnerId(), "owner_unlisted", Instant.now()));
        } else {
            publishUpdated(listing);
        }
        return detail(listingId, callerId);
    }

    /**
     * A moderator taking a listing down. Deliberately not {@code setStatus} with
     * the ownership check waived — the reason travels on the event, so a
     * consumer can tell "the owner withdrew it" from "we removed it".
     */
    @Transactional
    public void unlistByModerator(UUID listingId, String note) {
        Listing listing = listings.findById(listingId)
                .orElseThrow(() -> ApiException.notFound("Listing"));
        listing.setStatus(Listing.Status.UNLISTED);
        listings.save(listing);
        outbox.publish(Topics.LISTING, listing.getId(), new RadiusEvents.ListingUnlisted(
                listing.getId(), listing.getOwnerId(),
                note == null || note.isBlank() ? "moderated" : "moderated: " + note.trim(),
                Instant.now()));
    }

    @Transactional
    public List<Dtos.PhotoDto> attachPhoto(UUID listingId, UUID callerId, String objectKey, String publicUrl) {
        owned(listingId, callerId);
        if (photos.countByListingId(listingId) >= MAX_PHOTOS) {
            throw ApiException.conflict("too_many_photos", "A listing takes " + MAX_PHOTOS + " photos");
        }
        int next = (int) photos.countByListingId(listingId);
        photos.save(new ListingPhoto(listingId, objectKey, publicUrl, next));

        listings.findById(listingId).ifPresent(this::publishUpdated);   // the card photo changed
        return photosOf(listingId);
    }

    @Transactional
    public void removePhoto(UUID listingId, UUID callerId, UUID photoId) {
        owned(listingId, callerId);
        photos.findById(photoId)
                .filter(p -> p.getListingId().equals(listingId))
                .ifPresent(photos::delete);
    }

    /**
     * Called from the RequestAccepted consumer. Keyed on the request id, so a
     * redelivered event does not double-book.
     */
    @Transactional
    public void blockDates(UUID listingId, LocalDate from, LocalDate to, UUID requestId) {
        if (requestId != null && blocks.existsByRequestId(requestId)) return;
        blocks.save(new AvailabilityBlock(listingId, from, to, "booked", requestId));
    }

    @Transactional(readOnly = true)
    public boolean isFree(UUID listingId, LocalDate from, LocalDate to) {
        return !blocks.overlaps(listingId, from, to);
    }

    // ---- read side ---------------------------------------------------------

    @Transactional(readOnly = true)
    public Dtos.ListingResponse detail(UUID listingId, UUID viewerId) {
        Listing listing = listings.findById(listingId)
                .orElseThrow(() -> ApiException.notFound("Listing"));
        if (!listing.isLive() && !listing.isOwnedBy(viewerId)) {
            throw ApiException.notFound("Listing");
        }

        MemberLocation owner = members.findById(listing.getOwnerId()).orElse(null);
        Double distanceKm = null;
        MemberLocation viewer = viewerId == null ? null : members.findById(viewerId).orElse(null);
        if (viewer != null && viewer.getLat() != null) {
            distanceKm = round1(io.radius.common.support.Geo.distanceMetres(
                    viewer.getLat(), viewer.getLon(), listing.getLat(), listing.getLon()) / 1000d);
        }

        return new Dtos.ListingResponse(
                listing.getId(), listing.getKind(), listing.getTitle(), listing.getDescription(),
                listing.getPriceMinor(), listing.getUnit(), listing.getDepositMinor(),
                listing.getBuyPriceMinor(), listing.getCurrency(), listing.getLat(), listing.getLon(),
                listing.getStatus(),
                tags.findByListing(listingId).stream().map(ListingTag::getSlug).sorted().toList(),
                photosOf(listingId),
                rules.findByListingId(listingId).stream()
                        .sorted(Comparator.comparingInt(AvailabilityRule::getWeekday))
                        .map(r -> new Dtos.AvailabilityRuleDto(r.getWeekday(), r.getFromTime(), r.getToTime()))
                        .toList(),
                ownerDto(listing.getOwnerId(), owner),
                distanceKm,
                listing.isOwnedBy(viewerId),
                listing.isHomeVisit(),
                listing.getRatingAvg(), listing.getRatingCount());
    }

    @Transactional(readOnly = true)
    public List<Dtos.ListingCard> mine(UUID ownerId) {
        List<Listing> mine = listings.findByOwnerIdOrderByCreatedAtDesc(ownerId);
        Map<UUID, String> firstPhoto = firstPhotos(mine.stream().map(Listing::getId).toList());
        MemberLocation me = members.findById(ownerId).orElse(null);
        return mine.stream()
                .map(l -> new Dtos.ListingCard(l.getId(), l.getKind(), l.getTitle(), l.getPriceMinor(),
                        l.getUnit(), l.getBuyPriceMinor(), l.getCurrency(), firstPhoto.get(l.getId()),
                        l.getLat(), l.getLon(), 0d, ownerDto(ownerId, me), l.getStatus(), l.isHomeVisit(),
                        l.getRatingAvg(), l.getRatingCount()))
                .toList();
    }

    /**
     * The caller's own point wins if they send one — the phone knows where it
     * is. Otherwise we use the home point mirrored from radius.user.v1.
     */
    @Transactional(readOnly = true)
    public Dtos.FeedPage feed(UUID viewerId, Double lat, Double lon, Integer radiusKm,
                              String kind, String cursor, Integer limit) {
        MemberLocation me = viewerId == null ? null : members.findById(viewerId).orElse(null);
        Double useLat = lat != null ? lat : (me == null ? null : me.getLat());
        Double useLon = lon != null ? lon : (me == null ? null : me.getLon());
        if (useLat == null || useLon == null) {
            throw ApiException.badRequest("no_location", viewerId == null
                    ? "Send lat and lon to browse without an account"
                    : "Set your area in your profile, or send lat and lon");
        }
        int useRadius = radiusKm != null ? radiusKm : (me == null ? 5 : me.getRadiusKm());
        int size = limit == null ? DEFAULT_PAGE : Math.clamp(limit, 1, 50);

        List<FeedQuery.FeedRow> rows = feedQuery.nearby(useLat, useLon, useRadius * 1000d, kind,
                viewerId, Cursor.decode(cursor), size + 1);

        boolean hasMore = rows.size() > size;
        List<FeedQuery.FeedRow> page = hasMore ? rows.subList(0, size) : rows;
        Map<UUID, String> firstPhoto = firstPhotos(page.stream().map(FeedQuery.FeedRow::id).toList());

        boolean guest = viewerId == null;
        List<Dtos.ListingCard> items = page.stream()
                .map(r -> new Dtos.ListingCard(r.id(), r.kind(), r.title(), r.priceMinor(), r.unit(),
                        r.buyPriceMinor(), r.currency(), firstPhoto.get(r.id()),
                        guest ? Geo.coarsen(r.lat(), r.lon(), GUEST_PRECISION_M)[0] : r.lat(),
                        guest ? Geo.coarsen(r.lat(), r.lon(), GUEST_PRECISION_M)[1] : r.lon(),
                        round1(r.distanceMetres() / 1000d),
                        new Dtos.OwnerDto(r.ownerId(), r.ownerName(), r.ownerPhoto(), r.areaLabel(),
                                r.ownerIdChecked(), r.ownerProfessional(), r.ownerTrade()),
                        "LIVE", r.homeVisit(), r.ratingAvg(), r.ratingCount()))
                .toList();

        String next = null;
        if (hasMore && !page.isEmpty()) {
            FeedQuery.FeedRow last = page.get(page.size() - 1);
            next = new Cursor(last.distanceMetres(), last.id().toString()).encode();
        }
        return new Dtos.FeedPage(items, next, useRadius);
    }

    @Transactional(readOnly = true)
    public Dtos.AvailabilityResponse availability(UUID listingId) {
        listings.findById(listingId).orElseThrow(() -> ApiException.notFound("Listing"));
        return new Dtos.AvailabilityResponse(listingId,
                rules.findByListingId(listingId).stream()
                        .map(r -> new Dtos.AvailabilityRuleDto(r.getWeekday(), r.getFromTime(), r.getToTime()))
                        .toList(),
                blocks.findByListingIdAndDateToGreaterThanEqualOrderByDateFromAsc(listingId, LocalDate.now())
                        .stream()
                        .map(b -> new Dtos.BlockedRange(b.getDateFrom(), b.getDateTo(), b.getReason()))
                        .toList());
    }

    /**
     * Fed by radius.user.v1. Each half is updated on its own event and must not
     * clobber the other: an ID check arriving does not tell us anything about
     * the trade approval, and vice versa.
     */
    @Transactional
    public void updateMemberIdCheck(UUID userId, boolean idChecked) {
        MemberLocation location = members.findById(userId).orElseGet(() -> new MemberLocation(userId));
        location.updateChecks(idChecked, location.isProfessional(), location.getTrade());
        members.save(location);
    }

    @Transactional
    public void updateMemberProfessional(UUID userId, boolean professional, String trade) {
        MemberLocation location = members.findById(userId).orElseGet(() -> new MemberLocation(userId));
        location.updateChecks(location.isIdChecked(), professional, trade);
        members.save(location);
    }

    /**
     * Stamped from UserRegistered. It is what lets PostingLimiter give a
     * days-old account a tighter bucket than a member who has been here a year.
     */
    @Transactional
    public void registerMember(UUID userId, Instant at) {
        MemberLocation location = members.findById(userId).orElseGet(() -> new MemberLocation(userId));
        location.registeredAt(at == null ? Instant.now() : at);
        members.save(location);
    }

    /**
     * A completed booking, projected from radius.request.v1. The request id is
     * the primary key, so a redelivery writes the same row rather than a second
     * one — no Redis guard needed for this consumer.
     */
    @Transactional
    public void recordCompletedBooking(UUID requestId, UUID listingId, UUID userId, Instant at) {
        if (completedBookings.existsById(requestId)) return;
        completedBookings.save(new CompletedBooking(requestId, listingId, userId, at));
    }

    /** Kept for the member_location projection fed by radius.user.v1. */
    @Transactional
    public void upsertMember(UUID userId, String displayName, String photoUrl, String areaLabel,
                             Double lat, Double lon, int radiusKm) {
        MemberLocation location = members.findById(userId).orElseGet(() -> new MemberLocation(userId));
        location.update(displayName, photoUrl, areaLabel, lat, lon, radiusKm);
        members.save(location);
    }

    // ---- internals ---------------------------------------------------------

    private Listing owned(UUID listingId, UUID callerId) {
        Listing listing = listings.findById(listingId)
                .orElseThrow(() -> ApiException.notFound("Listing"));
        if (!listing.isOwnedBy(callerId)) {
            throw ApiException.forbidden("That listing is not yours");
        }
        return listing;
    }

    /**
     * The gate behind a home visit. Both halves matter: the ID check says a
     * person was looked at, the ACTIVE professional profile says their trade
     * paperwork was too. member_location is fed by radius.user.v1, so this is a
     * local read rather than a call to user-service.
     */
    private void requireCheckedProfessional(UUID ownerId) {
        MemberLocation member = members.findById(ownerId).orElse(null);
        if (member == null || !member.isIdChecked()) {
            throw ApiException.conflict("id_check_first",
                    "We check ID before anyone lists a service that visits homes");
        }
        if (!member.isProfessional()) {
            throw ApiException.conflict("professional_check_first",
                    "Your professional details need approving before you can offer home visits");
        }
    }

    private static void requirePrice(Listing.Kind kind, Long priceMinor, Long buyPriceMinor) {
        if (kind == Listing.Kind.OPEN_NEED) return;
        if (priceMinor == null && buyPriceMinor == null) {
            throw ApiException.badRequest("price_required", "Give a rate or a sale price");
        }
    }

    private void replaceTags(UUID listingId, List<String> slugs) {
        tags.deleteByListing(listingId);
        if (slugs == null) return;
        slugs.stream().map(String::trim).filter(s -> !s.isEmpty()).distinct()
                .forEach(slug -> tags.save(new ListingTag(listingId, slug)));
    }

    private void replaceAvailability(UUID listingId, List<Dtos.AvailabilityRuleDto> weekly) {
        rules.deleteByListing(listingId);
        if (weekly == null) return;
        weekly.forEach(r -> rules.save(new AvailabilityRule(listingId, r.weekday(), r.from(), r.to())));
    }

    private List<Dtos.PhotoDto> photosOf(UUID listingId) {
        return photos.findByListingIdOrderBySortOrderAsc(listingId).stream()
                .map(p -> new Dtos.PhotoDto(p.getId(), p.getUrl(), p.getSortOrder()))
                .toList();
    }

    private Map<UUID, String> firstPhotos(List<UUID> listingIds) {
        if (listingIds.isEmpty()) return Map.of();
        return photos.findByListingIdInOrderBySortOrderAsc(listingIds).stream()
                .collect(Collectors.toMap(ListingPhoto::getListingId, ListingPhoto::getUrl,
                        (first, second) -> first));
    }

    private Dtos.OwnerDto ownerDto(UUID ownerId, MemberLocation location) {
        return new Dtos.OwnerDto(ownerId,
                location == null ? null : location.getDisplayName(),
                location == null ? null : location.getPhotoUrl(),
                location == null ? null : location.getAreaLabel(),
                location != null && location.isIdChecked(),
                location != null && location.isProfessional(),
                location == null ? null : location.getTrade());
    }

    private void publishPublished(Listing listing, List<String> slugs) {
        outbox.publish(Topics.LISTING, listing.getId(), new RadiusEvents.ListingPublished(
                listing.getId(), listing.getOwnerId(), listing.getKind(), listing.getTitle(),
                listing.getDescription(), listing.getPriceMinor(), listing.getUnit(),
                listing.getDepositMinor(), listing.getBuyPriceMinor(), listing.getLat(), listing.getLon(),
                slugs == null ? List.of() : slugs, null, Instant.now()));
    }

    private void publishUpdated(Listing listing) {
        List<String> slugs = tags.findByListing(listing.getId()).stream().map(ListingTag::getSlug).toList();
        String photoUrl = photos.findByListingIdOrderBySortOrderAsc(listing.getId()).stream()
                .findFirst().map(ListingPhoto::getUrl).orElse(null);
        outbox.publish(Topics.LISTING, listing.getId(), new RadiusEvents.ListingUpdated(
                listing.getId(), listing.getOwnerId(), listing.getKind(), listing.getTitle(),
                listing.getDescription(), listing.getPriceMinor(), listing.getUnit(),
                listing.getDepositMinor(), listing.getBuyPriceMinor(), listing.getLat(), listing.getLon(),
                slugs, photoUrl, listing.getStatus(), Instant.now()));
    }

    private static double round1(double value) {
        return Math.round(value * 10d) / 10d;
    }
}
