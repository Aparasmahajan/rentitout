package io.radius.listing.repo;

import io.radius.listing.domain.Report;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ReportRepository extends JpaRepository<Report, UUID> {

    boolean existsByReporterIdAndTargetTypeAndTargetId(UUID reporterId, String targetType, UUID targetId);

    Optional<Report> findByReporterIdAndTargetTypeAndTargetId(UUID reporterId, String targetType, UUID targetId);

    List<Report> findByStateOrderByCreatedAtAsc(String state);

    /** The whole queue, oldest first — a report left waiting is the one that matters. */
    List<Report> findAllByOrderByCreatedAtAsc();

    List<Report> findByTargetTypeAndTargetIdOrderByCreatedAtAsc(String targetType, UUID targetId);
}
