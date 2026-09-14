package io.radius.user.repo;

import io.radius.user.domain.Tag;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TagRepository extends JpaRepository<Tag, UUID> {

    List<Tag> findAllByKindOrderByLabelAsc(String kind);

    Optional<Tag> findBySlug(String slug);

    List<Tag> findAllByIdIn(List<UUID> ids);
}
