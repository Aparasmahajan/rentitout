package io.radius.listing.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * A projection of radius.user.v1. It exists so rendering a feed card needs no
 * call to user-service — this service owns a copy of the few fields a card shows.
 */
@Entity
@Table(name = "member_location")
public class MemberLocation {

    @Id
    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "display_name")
    private String displayName;

    @Column(name = "photo_url")
    private String photoUrl;

    @Column(name = "area_label")
    private String areaLabel;

    private Double lat;
    private Double lon;

    @Column(name = "radius_km", nullable = false)
    private int radiusKm = 5;

    @Column(name = "id_checked", nullable = false)
    private boolean idChecked;

    @Column(nullable = false)
    private boolean professional;

    private String trade;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected MemberLocation() {}

    public MemberLocation(UUID userId) {
        this.userId = userId;
    }

    public UUID getUserId() { return userId; }
    public String getDisplayName() { return displayName; }
    public String getPhotoUrl() { return photoUrl; }
    public String getAreaLabel() { return areaLabel; }
    public Double getLat() { return lat; }
    public Double getLon() { return lon; }
    public int getRadiusKm() { return radiusKm; }
    public boolean isIdChecked() { return idChecked; }
    public boolean isProfessional() { return professional; }
    public String getTrade() { return trade; }

    /** Fed by radius.user.v1 - never written from a request handler. */
    public void updateChecks(boolean idChecked, boolean professional, String trade) {
        this.idChecked = idChecked;
        this.professional = professional;
        this.trade = trade;
        this.updatedAt = Instant.now();
    }

    public void update(String displayName, String photoUrl, String areaLabel,
                       Double lat, Double lon, int radiusKm) {
        this.displayName = displayName;
        this.photoUrl = photoUrl;
        this.areaLabel = areaLabel;
        this.lat = lat;
        this.lon = lon;
        this.radiusKm = radiusKm;
        this.updatedAt = Instant.now();
    }
}
