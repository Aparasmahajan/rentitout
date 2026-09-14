package io.radius.payment.repo;

import io.radius.payment.domain.FeeConfig;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface FeeConfigRepository extends JpaRepository<FeeConfig, UUID> {

    Optional<FeeConfig> findByKind(String kind);

    /** The row with a null kind is the default that applies to every listing kind. */
    Optional<FeeConfig> findFirstByKindIsNull();
}
