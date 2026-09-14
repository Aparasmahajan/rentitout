package io.radius.user.repo;

import io.radius.user.domain.VerificationRequest;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface VerificationRequestRepository extends JpaRepository<VerificationRequest, UUID> {

    List<VerificationRequest> findByUserIdOrderBySubmittedAtDesc(UUID userId);

    @Query("""
            select v from VerificationRequest v
            where v.userId = :userId and v.kind = :kind and v.state in ('SUBMITTED', 'IN_REVIEW')
            """)
    Optional<VerificationRequest> findOpen(@Param("userId") UUID userId, @Param("kind") String kind);

    @Query("""
            select v from VerificationRequest v
            where v.state in ('SUBMITTED', 'IN_REVIEW')
            order by v.submittedAt asc
            """)
    List<VerificationRequest> findQueue(Limit limit);

    @Query("select v from VerificationRequest v where v.state = :state order by v.submittedAt desc")
    List<VerificationRequest> findByState(@Param("state") String state, Limit limit);

    @Query("""
            select count(v) from VerificationRequest v
            where v.userId = :userId and v.kind = :kind and v.state = 'APPROVED'
            """)
    long countApproved(@Param("userId") UUID userId, @Param("kind") String kind);
}
