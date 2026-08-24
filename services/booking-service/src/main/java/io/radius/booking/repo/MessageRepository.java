package io.radius.booking.repo;

import io.radius.booking.domain.Message;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface MessageRepository extends JpaRepository<Message, UUID> {

    List<Message> findByRequestIdOrderBySentAtAsc(UUID requestId);

    @Query("""
            select count(m) from Message m
            where m.requestId = :requestId and m.senderId <> :readerId and m.readAt is null
            """)
    long countUnread(@Param("requestId") UUID requestId, @Param("readerId") UUID readerId);

    @Modifying
    @Query("""
            update Message m set m.readAt = current_timestamp
            where m.requestId = :requestId and m.senderId <> :readerId and m.readAt is null
            """)
    int markThreadRead(@Param("requestId") UUID requestId, @Param("readerId") UUID readerId);
}
