package io.radius.payment.repo;

import io.radius.payment.domain.GatewayEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

/** Proof we already processed a gateway callback. This is what makes the webhook idempotent. */
public interface GatewayEventRepository extends JpaRepository<GatewayEvent, UUID> {

    boolean existsByGatewayRef(String gatewayRef);
}
