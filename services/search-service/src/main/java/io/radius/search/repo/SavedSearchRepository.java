package io.radius.search.repo;

import io.radius.search.domain.SavedSearch;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface SavedSearchRepository extends JpaRepository<SavedSearch, UUID> {

    List<SavedSearch> findByUserIdOrderByCreatedAtDesc(UUID userId);

    List<SavedSearch> findByDigestFrequency(String digestFrequency);
}
