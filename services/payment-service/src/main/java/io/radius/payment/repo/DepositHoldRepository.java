package io.radius.payment.repo;

import io.radius.payment.domain.DepositHold;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface DepositHoldRepository extends JpaRepository<DepositHold, UUID> {

    Optional<DepositHold> findByRequestId(UUID requestId);
}
