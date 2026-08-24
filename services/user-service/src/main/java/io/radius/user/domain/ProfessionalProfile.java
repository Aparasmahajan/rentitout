package io.radius.user.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * The extra detail behind someone who will come to your flat.
 *
 * The shop point is stored exactly — it is a business address the professional
 * chose to publish, and a call-out radius drawn around a fuzzed point would be
 * wrong by up to 100 m in every direction. Contrast {@link Profile#setHome}.
 */
@Entity
@Table(name = "professional_profile")
public class ProfessionalProfile {

    /** Trades that imply someone entering a home. Kept short and concrete. */
    public enum Trade {
        ELECTRICIAN, PLUMBER, AC_SERVICE, APPLIANCE_REPAIR, CARPENTER, PAINTER,
        PEST_CONTROL, CLEANING, HOUSE_HELP, COOK, DRIVER, MOVER, GARDENER,
        BEAUTICIAN, TUTOR, IT_SUPPORT, OTHER
    }

    public enum State { PENDING, ACTIVE, SUSPENDED }

    @Id
    @Column(name = "user_id")
    private UUID userId;

    @Column(nullable = false)
    private String trade;

    @Column(name = "business_name")
    private String businessName;

    private String about;

    @Column(name = "shop_address")
    private String shopAddress;

    @Column(name = "shop_lat")
    private Double shopLat;

    @Column(name = "shop_lon")
    private Double shopLon;

    @Column(name = "service_radius_km", nullable = false)
    private int serviceRadiusKm = 10;

    @Column(name = "years_experience")
    private Integer yearsExperience;

    @Column(name = "licence_ref")
    private String licenceRef;

    @Column(name = "insurance_ref")
    private String insuranceRef;

    private String languages;

    @Column(name = "contact_phone")
    private String contactPhone;

    @Column(nullable = false)
    private String state = State.PENDING.name();

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected ProfessionalProfile() {}

    public ProfessionalProfile(UUID userId, Trade trade) {
        this.userId = userId;
        this.trade = trade.name();
    }

    @PreUpdate
    void touch() { this.updatedAt = Instant.now(); }

    public UUID getUserId() { return userId; }
    public String getTrade() { return trade; }
    public String getBusinessName() { return businessName; }
    public String getAbout() { return about; }
    public String getShopAddress() { return shopAddress; }
    public Double getShopLat() { return shopLat; }
    public Double getShopLon() { return shopLon; }
    public int getServiceRadiusKm() { return serviceRadiusKm; }
    public Integer getYearsExperience() { return yearsExperience; }
    public String getLicenceRef() { return licenceRef; }
    public String getInsuranceRef() { return insuranceRef; }
    public String getLanguages() { return languages; }
    public String getContactPhone() { return contactPhone; }
    public String getState() { return state; }
    public Instant getCreatedAt() { return createdAt; }

    public boolean isActive() { return State.ACTIVE.name().equals(state); }

    public void setTrade(Trade trade) { this.trade = trade.name(); }
    public void setBusinessName(String v) { this.businessName = v; }
    public void setAbout(String v) { this.about = v; }
    public void setShopAddress(String v) { this.shopAddress = v; }
    public void setShop(Double lat, Double lon) { this.shopLat = lat; this.shopLon = lon; }
    public void setServiceRadiusKm(int v) { this.serviceRadiusKm = v; }
    public void setYearsExperience(Integer v) { this.yearsExperience = v; }
    public void setLicenceRef(String v) { this.licenceRef = v; }
    public void setInsuranceRef(String v) { this.insuranceRef = v; }
    public void setLanguages(String v) { this.languages = v; }
    public void setContactPhone(String v) { this.contactPhone = v; }

    public void activate() { this.state = State.ACTIVE.name(); this.updatedAt = Instant.now(); }
    public void suspend() { this.state = State.SUSPENDED.name(); this.updatedAt = Instant.now(); }
}
