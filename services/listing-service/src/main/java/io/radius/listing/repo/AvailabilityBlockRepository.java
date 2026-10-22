package io.radius.listing.repo;

import io.radius.listing.domain.AvailabilityBlock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface AvailabilityBlockRepository extends JpaRepository<AvailabilityBlock, UUID> {

    List<AvailabilityBlock> findByListingIdAndDateToGreaterThanEqualOrderByDateFromAsc(
            UUID listingId, LocalDate from);

    boolean existsByRequestId(UUID requestId);

    /** Only booked blocks carry a request id, so manual blocks are never touched. */
    @Modifying
    @Query("delete from AvailabilityBlock b where b.requestId = :requestId")
    int deleteByRequestId(@Param("requestId") UUID requestId);

    @Query("""
            select case when count(b) > 0 then true else false end from AvailabilityBlock b
            where b.listingId = :listingId and b.dateFrom <= :to and b.dateTo >= :from
            """)
    boolean overlaps(@Param("listingId") UUID listingId,
                     @Param("from") LocalDate from, @Param("to") LocalDate to);
}
