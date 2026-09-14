package io.radius.listing.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

/** Tags are stored as slugs, not as foreign keys: the vocabulary lives in user-service. */
@Entity
@Table(name = "listing_tag")
public class ListingTag {

    @Embeddable
    public static class Key implements Serializable {

        @Column(name = "listing_id")
        private UUID listingId;

        private String slug;

        protected Key() {}

        Key(UUID listingId, String slug) {
            this.listingId = listingId;
            this.slug = slug;
        }

        public UUID getListingId() { return listingId; }
        public String getSlug() { return slug; }

        @Override
        public boolean equals(Object o) {
            return o instanceof Key k && Objects.equals(listingId, k.listingId) && Objects.equals(slug, k.slug);
        }

        @Override
        public int hashCode() { return Objects.hash(listingId, slug); }
    }

    @EmbeddedId
    private Key id;

    protected ListingTag() {}

    public ListingTag(UUID listingId, String slug) {
        this.id = new Key(listingId, slug);
    }

    public String getSlug() { return id.getSlug(); }
    public UUID getListingId() { return id.getListingId(); }
}
