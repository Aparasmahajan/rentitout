package io.radius.booking.repo;

import io.radius.booking.domain.RequestTransition;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface TransitionRepository extends JpaRepository<RequestTransition, UUID> {

    List<RequestTransition> findByRequestIdOrderByAtAsc(UUID requestId);
}
