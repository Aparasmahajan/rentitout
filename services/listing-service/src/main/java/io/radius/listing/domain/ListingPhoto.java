package io.radius.listing.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

/** The object key is what the service stores; the bytes never pass through it. */
@Entity
@Table(name = "listing_photo")
public class ListingPhoto {

    @Id
    private UUID id;

    @Column(name = "listing_id", nullable = false)
    private UUID listingId;

    @Column(name = "object_key", nullable = false)
    private String objectKey;

    @Column(nullable = false)
    private String url;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    protected ListingPhoto() {}

    public ListingPhoto(UUID listingId, String objectKey, String url, int sortOrder) {
        this.id = UUID.randomUUID();
        this.listingId = listingId;
        this.objectKey = objectKey;
        this.url = url;
        this.sortOrder = sortOrder;
    }

    public UUID getId() { return id; }
    public UUID getListingId() { return listingId; }
    public String getObjectKey() { return objectKey; }
    public String getUrl() { return url; }
    public int getSortOrder() { return sortOrder; }
}
