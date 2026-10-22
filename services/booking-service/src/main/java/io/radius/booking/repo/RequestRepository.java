package io.radius.booking.repo;

import io.radius.booking.domain.BookingRequest;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface RequestRepository extends JpaRepository<BookingRequest, UUID> {

    List<BookingRequest> findByRequesterIdOrderByCreatedAtDesc(UUID requesterId);

    List<BookingRequest> findByOwnerIdOrderByCreatedAtDesc(UUID ownerId);

    @Query("select r from BookingRequest r where r.status = 'SENT' and r.expiresAt < :now")
    List<BookingRequest> findExpired(@Param("now") Instant now, Limit limit);

    /** Who may listen to a chat thread: only the two people on the request. */
    @Query("""
            select case when count(r) > 0 then true else false end from BookingRequest r
            where r.id = :requestId and (r.requesterId = :userId or r.ownerId = :userId)
            """)
    boolean isParty(@Param("requestId") UUID requestId, @Param("userId") UUID userId);

    /** Guards against two people booking the same days while the owner sleeps. */
    @Query("""
            select count(r) from BookingRequest r
            where r.listingId = :listingId
              and r.status in ('ACCEPTED', 'IN_PROGRESS')
              and r.startDate <= :to and r.endDate >= :from
            """)
    long countOverlapping(@Param("listingId") UUID listingId,
                          @Param("from") LocalDate from, @Param("to") LocalDate to);
}
