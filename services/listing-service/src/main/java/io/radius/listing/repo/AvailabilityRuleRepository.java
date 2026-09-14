package io.radius.listing.repo;

import io.radius.listing.domain.AvailabilityRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface AvailabilityRuleRepository extends JpaRepository<AvailabilityRule, UUID> {

    List<AvailabilityRule> findByListingId(UUID listingId);

    @Modifying
    @Query("delete from AvailabilityRule r where r.listingId = :listingId")
    void deleteByListing(@Param("listingId") UUID listingId);
}
