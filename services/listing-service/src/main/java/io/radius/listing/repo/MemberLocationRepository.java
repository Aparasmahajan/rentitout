package io.radius.listing.repo;

import io.radius.listing.domain.MemberLocation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

/** The projection of radius.user.v1 this service keeps so a card render needs no remote call. */
public interface MemberLocationRepository extends JpaRepository<MemberLocation, UUID> {
}
