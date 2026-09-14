package io.radius.listing.repo;

import io.radius.listing.domain.ListingComment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ListingCommentRepository extends JpaRepository<ListingComment, UUID> {

    /**
     * Deleted comments come back too. The thread renders them as a tombstone, so
     * a reply underneath a removed comment still has something to hang from.
     */
    List<ListingComment> findByListingIdOrderByCreatedAtAsc(UUID listingId);

    long countByListingIdAndDeletedAtIsNull(UUID listingId);
}
