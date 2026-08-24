package io.radius.notification.repo;

import io.radius.notification.domain.Notification;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    List<Notification> findByUserIdOrderByCreatedAtDesc(UUID userId, Limit limit);

    long countByUserIdAndReadAtIsNull(UUID userId);

    boolean existsByEventId(String eventId);

    @Modifying
    @Query("""
            update Notification n set n.readAt = current_timestamp, n.state = 'READ'
            where n.userId = :userId and n.readAt is null
            """)
    int markAllRead(@Param("userId") UUID userId);
}
