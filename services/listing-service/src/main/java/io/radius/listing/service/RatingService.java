package io.radius.listing.service;

import io.radius.common.web.ApiException;
import io.radius.listing.api.ModerationDtos;
import io.radius.listing.domain.CompletedBooking;
import io.radius.listing.domain.Listing;
import io.radius.listing.domain.ListingRating;
import io.radius.listing.repo.CompletedBookingRepository;
import io.radius.listing.repo.ListingRatingRepository;
import io.radius.listing.repo.ListingRepository;
import io.radius.listing.repo.MemberLocationRepository;
import io.radius.listing.repo.RatingAggregate;
import io.radius.listing.support.PostingLimiter;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Ratings on a listing, Amazon's middle path: anyone may rate, and a rating
 * that came from someone who actually completed a booking carries a badge and
 * sorts first. Requiring a booking makes a new listing page useless; requiring
 * nothing makes every page worthless. This does both.
 *
 * One rating per member per listing, enforced by a unique index — changing your
 * mind edits the row rather than adding a second opinion.
 */
@Service
public class RatingService {

    private final ListingRatingRepository ratings;
    private final ListingRepository listings;
    private final CompletedBookingRepository bookings;
    private final MemberLocationRepository members;
    private final PostingLimiter limiter;

    public RatingService(ListingRatingRepository ratings, ListingRepository listings,
                         CompletedBookingRepository bookings, MemberLocationRepository members,
                         PostingLimiter limiter) {
        this.ratings = ratings;
        this.listings = listings;
        this.bookings = bookings;
        this.members = members;
        this.limiter = limiter;
    }

    /**
     * Post or replace. An existing rating is edited rather than rejected — the
     * unique index means there is only ever one, and "you already rated this"
     * is an error message that helps nobody.
     */
    @Transactional
    public ModerationDtos.RatingView rate(UUID listingId, UUID authorId, ModerationDtos.CreateRating req) {
        Listing listing = listings.findById(listingId)
                .orElseThrow(() -> ApiException.notFound("Listing"));
        if (listing.isOwnedBy(authorId)) {
            throw ApiException.forbidden("You cannot rate your own listing");
        }

        ListingRating existing = ratings.findByListingIdAndAuthorId(listingId, authorId).orElse(null);
        short stars = (short) req.stars();

        ListingRating rating;
        if (existing == null) {
            limiter.checkRating(authorId);
            rating = ratings.save(new ListingRating(listingId, authorId, stars,
                    trimToNull(req.title()), trimToNull(req.body()), verifiedRequest(listingId, authorId)));
        } else if (existing.wasSelfRemoved()) {
            // They deleted their own and are back. The unique index allows one
            // row per member per listing, so this has to reuse it — otherwise
            // "remove mine" would silently be permanent.
            limiter.checkRating(authorId);
            existing.restore(stars, trimToNull(req.title()), trimToNull(req.body()),
                    verifiedRequest(listingId, authorId));
            rating = existing;
        } else if (existing.isDeleted()) {
            // A moderated rating stays moderated. Letting the author write over
            // it would make removal a step in re-posting it.
            throw ApiException.conflict("rating_removed", "That rating was removed");
        } else {
            existing.edit(stars, trimToNull(req.title()), trimToNull(req.body()));
            rating = existing;
        }

        recompute(listing);
        return view(rating, authorId, false);
    }

    /** Author or admin. The listing owner deliberately cannot delete a rating of their own listing. */
    @Transactional
    public void delete(UUID ratingId, UUID callerId, boolean isAdmin) {
        ListingRating rating = load(ratingId);
        if (!isAdmin && !rating.getAuthorId().equals(callerId)) {
            throw ApiException.forbidden("You cannot remove that rating");
        }
        rating.delete(callerId);
        listings.findById(rating.getListingId()).ifPresent(this::recompute);
    }

    @Transactional(readOnly = true)
    public ModerationDtos.RatingSummary summary(UUID listingId, UUID callerId, boolean isAdmin) {
        // Ratings for a listing that does not exist are a 404, not an empty page.
        if (!listings.existsById(listingId)) throw ApiException.notFound("Listing");

        List<ListingRating> rows = ratings.findByListingIdAndDeletedAtIsNullOrderByCreatedAtDesc(listingId);

        Map<Integer, Long> histogram = new LinkedHashMap<>();
        for (Object[] row : ratings.histogram(listingId)) {
            histogram.put(((Number) row[0]).intValue(), ((Number) row[1]).longValue());
        }

        // Verified bookings first, then newest. A rating from someone who
        // actually transacted is the one a reader wants at the top.
        Comparator<ListingRating> order = Comparator
                .comparing(ListingRating::isVerifiedBooking, Comparator.reverseOrder())
                .thenComparing(ListingRating::getCreatedAt, Comparator.reverseOrder());

        List<ModerationDtos.RatingView> views = rows.stream()
                .sorted(order)
                .map(r -> view(r, callerId, isAdmin))
                .toList();

        ModerationDtos.RatingView mine = callerId == null ? null : views.stream()
                .filter(v -> callerId.equals(v.authorId()))
                .findFirst().orElse(null);

        RatingAggregate agg = ratings.aggregate(listingId);
        Double average = agg == null || agg.count() == null || agg.count() == 0
                ? null
                : round2(agg.averageOrZero());

        return new ModerationDtos.RatingSummary(average, views.size(), histogram, mine, views);
    }

    /** The completed booking that earns the badge, or null if there is not one. */
    private UUID verifiedRequest(UUID listingId, UUID authorId) {
        return bookings.findFirstByListingIdAndUserIdOrderByCompletedAtAsc(listingId, authorId)
                .map(CompletedBooking::getRequestId)
                .orElse(null);
    }

    ListingRating load(UUID id) {
        return ratings.findById(id).orElseThrow(() -> ApiException.notFound("Rating"));
    }

    /**
     * Recomputed from the rows, never incremented in place. An increment gets
     * one delete or one edit wrong and the number is quietly wrong forever;
     * an aggregate over a few dozen rows is cheap and cannot drift.
     */
    private void recompute(Listing listing) {
        RatingAggregate agg = ratings.aggregate(listing.getId());
        int count = agg == null ? 0 : agg.countOrZero();
        BigDecimal average = count == 0 ? null
                : BigDecimal.valueOf(agg.averageOrZero()).setScale(2, RoundingMode.HALF_UP);
        listing.setRating(average, count);
        listings.save(listing);
    }

    private ModerationDtos.RatingView view(ListingRating r, UUID callerId, boolean isAdmin) {
        boolean mine = callerId != null && r.getAuthorId().equals(callerId);
        return new ModerationDtos.RatingView(
                r.getId(), r.getListingId(), r.getAuthorId(),
                r.isDeleted() ? null : nameOf(r.getAuthorId()),
                r.getStars(), r.getTitle(), r.getBody(),
                r.isVerifiedBooking(), r.getCreatedAt(), r.getEditedAt(),
                mine && !r.isDeleted(),
                !r.isDeleted() && (mine || isAdmin));
    }

    private String nameOf(UUID userId) {
        return members.findById(userId).map(m -> m.getDisplayName()).orElse("A neighbour");
    }

    private static String trimToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static double round2(double value) {
        return Math.round(value * 100d) / 100d;
    }
}
