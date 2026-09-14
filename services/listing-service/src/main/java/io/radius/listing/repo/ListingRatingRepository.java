package io.radius.listing.repo;

import io.radius.listing.domain.ListingRating;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ListingRatingRepository extends JpaRepository<ListingRating, UUID> {

    Optional<ListingRating> findByListingIdAndAuthorId(UUID listingId, UUID authorId);

    List<ListingRating> findByListingIdAndDeletedAtIsNullOrderByCreatedAtDesc(UUID listingId);

    /**
     * The average and count that get denormalised onto the listing. Deleted
     * ratings are excluded here rather than at the call site, so a moderated
     * rating stops counting the moment it is removed.
     */
    @Query("""
           select new io.radius.listing.repo.RatingAggregate(avg(r.stars), count(r))
             from ListingRating r
            where r.listingId = :listingId and r.deletedAt is null
           """)
    RatingAggregate aggregate(@Param("listingId") UUID listingId);

    /** Star -> how many, for the histogram. Absent stars simply do not appear. */
    @Query("""
           select r.stars, count(r)
             from ListingRating r
            where r.listingId = :listingId and r.deletedAt is null
            group by r.stars
           """)
    List<Object[]> histogram(@Param("listingId") UUID listingId);
}
