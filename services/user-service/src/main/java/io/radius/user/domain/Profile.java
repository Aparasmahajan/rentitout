package io.radius.user.domain;

import io.radius.common.support.Geo;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * The public face of a member. home_point is a generated column in Postgres —
 * we write lat/lon, PostGIS derives and indexes the geography.
 */
@Entity
@Table(name = "profile")
public class Profile {

    @Id
    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "area_label")
    private String areaLabel;

    private Double lat;
    private Double lon;

    @Column(name = "search_radius_km", nullable = false)
    private int searchRadiusKm = 5;

    @Column(name = "open_to_requests", nullable = false)
    private boolean openToRequests = true;

    private String bio;

    @Column(name = "rating_avg")
    private BigDecimal ratingAvg;

    @Column(name = "completed_count", nullable = false)
    private int completedCount;

    @Column(nullable = false)
    private boolean verified;

    @Column(name = "verified_at")
    private Instant verifiedAt;

    /** What the check was based on, for the audit trail: "admin" today, a provider later. */
    @Column(name = "verified_by")
    private String verifiedBy;

    @Column(nullable = false)
    private boolean professional;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected Profile() {}

    public Profile(UUID userId) {
        this.userId = userId;
    }

    /** Snapped to ~100 m before it is ever stored. */
    public void setHome(double lat, double lon) {
        this.lat = Geo.roundLat(lat);
        this.lon = Geo.roundLon(lon, lat);
    }

    @PreUpdate
    void touch() { this.updatedAt = Instant.now(); }

    public UUID getUserId() { return userId; }
    public String getAreaLabel() { return areaLabel; }
    public Double getLat() { return lat; }
    public Double getLon() { return lon; }
    public int getSearchRadiusKm() { return searchRadiusKm; }
    public boolean isOpenToRequests() { return openToRequests; }
    public String getBio() { return bio; }
    public BigDecimal getRatingAvg() { return ratingAvg; }
    public int getCompletedCount() { return completedCount; }
    public boolean isVerified() { return verified; }
    public Instant getVerifiedAt() { return verifiedAt; }
    public String getVerifiedBy() { return verifiedBy; }
    public boolean isProfessional() { return professional; }

    public void markIdChecked(String by) {
        this.verified = true;
        this.verifiedAt = Instant.now();
        this.verifiedBy = by;
    }

    public void clearIdCheck() {
        this.verified = false;
        this.verifiedAt = null;
        this.verifiedBy = null;
    }

    public void setProfessional(boolean professional) { this.professional = professional; }

    public void setAreaLabel(String areaLabel) { this.areaLabel = areaLabel; }
    public void setSearchRadiusKm(int km) { this.searchRadiusKm = km; }
    public void setOpenToRequests(boolean open) { this.openToRequests = open; }
    public void setBio(String bio) { this.bio = bio; }
}
