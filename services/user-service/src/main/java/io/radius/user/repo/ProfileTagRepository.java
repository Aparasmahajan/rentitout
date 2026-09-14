package io.radius.user.repo;

import io.radius.user.domain.ProfileTag;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface ProfileTagRepository extends JpaRepository<ProfileTag, ProfileTag.Key> {

    @Query("select pt from ProfileTag pt where pt.id.profileId = :profileId")
    List<ProfileTag> findByProfile(@Param("profileId") UUID profileId);

    @Modifying
    @Query("""
            delete from ProfileTag pt
            where pt.id.profileId = :profileId and pt.id.tagId = :tagId and pt.id.relation = :relation
            """)
    int remove(@Param("profileId") UUID profileId, @Param("tagId") UUID tagId,
               @Param("relation") String relation);

    @Query("select count(pt) from ProfileTag pt where pt.id.profileId = :profileId")
    long countForProfile(@Param("profileId") UUID profileId);
}
