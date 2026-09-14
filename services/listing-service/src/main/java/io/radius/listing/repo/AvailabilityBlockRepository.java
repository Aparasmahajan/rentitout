package io.radius.listing.repo;

import io.radius.listing.domain.AvailabilityBlock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface AvailabilityBlockRepository extends JpaRepository<AvailabilityBlock, UUID> {

    List<AvailabilityBlock> findByListingIdAndDateToGreaterThanEqualOrderByDateFromAsc(
            UUID listingId, LocalDate from);

    boolean existsByRequestId(UUID requestId);

    @Query("""
            select case when count(b) > 0 then true else false end from AvailabilityBlock b
            where b.listingId = :listingId and b.dateFrom <= :to and b.dateTo >= :from
            """)
    boolean overlaps(@Param("listingId") UUID listingId,
                     @Param("from") LocalDate from, @Param("to") LocalDate to);
}
