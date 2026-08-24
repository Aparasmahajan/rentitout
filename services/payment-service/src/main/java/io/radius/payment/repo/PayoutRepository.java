package io.radius.payment.repo;

import io.radius.payment.domain.Payout;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PayoutRepository extends JpaRepository<Payout, UUID> {

    List<Payout> findByOwnerIdOrderByCreatedAtDesc(UUID ownerId);

    Optional<Payout> findByOwnerIdAndPeriod(UUID ownerId, String period);
}
