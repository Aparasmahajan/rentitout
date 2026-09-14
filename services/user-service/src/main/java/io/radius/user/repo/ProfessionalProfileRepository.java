package io.radius.user.repo;

import io.radius.user.domain.ProfessionalProfile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ProfessionalProfileRepository extends JpaRepository<ProfessionalProfile, UUID> {

    List<ProfessionalProfile> findByStateOrderByCreatedAtDesc(String state);
}
