package io.radius.listing.repo;

import io.radius.listing.domain.Listing;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ListingRepository extends JpaRepository<Listing, UUID> {

    List<Listing> findByOwnerIdOrderByCreatedAtDesc(UUID ownerId);
}
