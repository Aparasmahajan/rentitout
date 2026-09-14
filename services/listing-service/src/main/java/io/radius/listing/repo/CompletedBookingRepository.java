package io.radius.listing.repo;

import io.radius.listing.domain.CompletedBooking;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface CompletedBookingRepository extends JpaRepository<CompletedBooking, UUID> {

    /**
     * The earliest completed booking this member has on this listing. Earliest
     * rather than latest so that editing a rating years later keeps pointing at
     * the transaction that earned the badge.
     */
    Optional<CompletedBooking> findFirstByListingIdAndUserIdOrderByCompletedAtAsc(UUID listingId, UUID userId);
}
