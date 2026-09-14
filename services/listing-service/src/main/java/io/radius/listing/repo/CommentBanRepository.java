package io.radius.listing.repo;

import io.radius.listing.domain.CommentBan;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface CommentBanRepository extends JpaRepository<CommentBan, UUID> {

    /** Live bans only — expired rows stay for the audit trail but stop biting. */
    List<CommentBan> findByBannedUntilAfterOrderBySetAtDesc(Instant now);
}
