package io.radius.listing.repo;

import io.radius.listing.domain.ListingPhoto;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ListingPhotoRepository extends JpaRepository<ListingPhoto, UUID> {

    List<ListingPhoto> findByListingIdOrderBySortOrderAsc(UUID listingId);

    List<ListingPhoto> findByListingIdInOrderBySortOrderAsc(List<UUID> listingIds);

    long countByListingId(UUID listingId);
}
