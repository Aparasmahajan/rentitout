package io.radius.user.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

/** A member's relation to a tag: has it, teaches it, needs it, enjoys it. */
@Entity
@Table(name = "profile_tag")
public class ProfileTag {

    public enum Relation { has, teaches, needs, enjoys }

    @Embeddable
    public static class Key implements Serializable {

        @Column(name = "profile_id")
        private UUID profileId;

        @Column(name = "tag_id")
        private UUID tagId;

        @Column(name = "relation")
        private String relation;

        protected Key() {}

        Key(UUID profileId, UUID tagId, String relation) {
            this.profileId = profileId;
            this.tagId = tagId;
            this.relation = relation;
        }

        public UUID getProfileId() { return profileId; }
        public UUID getTagId() { return tagId; }
        public String getRelation() { return relation; }

        @Override
        public boolean equals(Object o) {
            return o instanceof Key k && Objects.equals(profileId, k.profileId)
                    && Objects.equals(tagId, k.tagId) && Objects.equals(relation, k.relation);
        }

        @Override
        public int hashCode() { return Objects.hash(profileId, tagId, relation); }
    }

    @EmbeddedId
    private Key id;

    protected ProfileTag() {}

    public ProfileTag(UUID profileId, UUID tagId, Relation relation) {
        this.id = new Key(profileId, tagId, relation.name());
    }

    public Key getId() { return id; }
    public UUID getTagId() { return id.getTagId(); }
    public String getRelation() { return id.getRelation(); }

    @Override
    public boolean equals(Object o) {
        return o instanceof ProfileTag other && Objects.equals(id, other.id);
    }

    @Override
    public int hashCode() { return Objects.hash(id); }
}
