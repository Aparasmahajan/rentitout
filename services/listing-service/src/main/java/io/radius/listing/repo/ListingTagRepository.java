package io.radius.listing.repo;

import io.radius.listing.domain.ListingTag;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface ListingTagRepository extends JpaRepository<ListingTag, ListingTag.Key> {

    @Query("select t from ListingTag t where t.id.listingId = :listingId")
    List<ListingTag> findByListing(@Param("listingId") UUID listingId);

    @Modifying
    @Query("delete from ListingTag t where t.id.listingId = :listingId")
    void deleteByListing(@Param("listingId") UUID listingId);
}
