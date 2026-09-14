package io.radius.user.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * One account. There is no owner role and no customer role — the duality is a
 * capability, so every member can list and every member can rent.
 */
@Entity
@Table(name = "app_user")
public class UserAccount {

    public enum Status { ACTIVE, SUSPENDED, DELETED }

    @Id
    private UUID id;

    private String phone;
    private String email;

    @Column(name = "password_hash")
    private String passwordHash;

    @Column(name = "display_name", nullable = false)
    private String displayName;

    @Column(name = "photo_url")
    private String photoUrl;

    @Column(nullable = false)
    private String status = Status.ACTIVE.name();

    /** MEMBER or ADMIN. Carried into the access token so services need no lookup. */
    @Column(nullable = false)
    private String role = "MEMBER";

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected UserAccount() {}

    public static UserAccount fromPhone(String phone, String displayName) {
        UserAccount u = new UserAccount();
        u.id = UUID.randomUUID();
        u.phone = phone;
        u.displayName = displayName;
        return u;
    }

    @PreUpdate
    void touch() { this.updatedAt = Instant.now(); }

    public UUID getId() { return id; }
    public String getPhone() { return phone; }
    public String getEmail() { return email; }
    public String getDisplayName() { return displayName; }
    public String getPhotoUrl() { return photoUrl; }
    public String getStatus() { return status; }
    public String getRole() { return role; }
    public Instant getCreatedAt() { return createdAt; }

    public void setDisplayName(String displayName) { this.displayName = displayName; }
    public void setPhotoUrl(String photoUrl) { this.photoUrl = photoUrl; }
    public void setEmail(String email) { this.email = email; }
    public boolean isActive() { return Status.ACTIVE.name().equals(status); }

    public void promoteToAdmin() { this.role = "ADMIN"; }
}
