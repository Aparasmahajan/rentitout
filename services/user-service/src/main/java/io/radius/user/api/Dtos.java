package io.radius.user.api;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** The boundary. Entities never leave the service layer. */
public final class Dtos {

    // ---- auth --------------------------------------------------------------
    public record OtpStartRequest(
            @NotBlank @Pattern(regexp = "\\+?[0-9]{8,15}", message = "Use digits, optionally with a leading +")
            String phone) {}

    /** {@code devCode} is populated only outside production, so the POC is testable. */
    public record OtpStartResponse(String phone, int expiresInSeconds, String devCode) {}

    public record OtpVerifyRequest(
            @NotBlank String phone,
            @NotBlank @Pattern(regexp = "[0-9]{6}") String code,
            @Size(max = 60) String displayName) {}

    public record TokenResponse(String accessToken, String refreshToken, long expiresIn, MeResponse me) {}

    public record RefreshRequest(@NotBlank String refreshToken) {}

    // ---- profile -----------------------------------------------------------
    public record TagRef(UUID id, String slug, String label, String kind, String relation) {}

    public record MeResponse(UUID id, String displayName, String phone, String photoUrl,
                             String areaLabel, Double lat, Double lon, int searchRadiusKm,
                             boolean openToRequests, String bio, BigDecimal ratingAvg,
                             int completedCount, boolean idChecked, java.time.Instant idCheckedAt,
                             boolean professional, String role, List<TagRef> tags) {}

    /** {@code idChecked} is deliberately not called "verified": it says what was looked at. */
    public record PublicProfileResponse(UUID id, String displayName, String photoUrl, String areaLabel,
                                        String bio, BigDecimal ratingAvg, int completedCount,
                                        boolean idChecked, java.time.Instant idCheckedAt,
                                        boolean professional, String trade, String businessName,
                                        boolean openToRequests, List<TagRef> tags,
                                        Double distanceKm) {}

    public record ProfilePatch(
            @Size(max = 60) String displayName,
            String photoUrl,
            @Size(max = 80) String areaLabel,
            @DecimalMin("-90.0") @DecimalMax("90.0") Double lat,
            @DecimalMin("-180.0") @DecimalMax("180.0") Double lon,
            @Min(1) @Max(50) Integer searchRadiusKm,
            Boolean openToRequests,
            @Size(max = 500) String bio) {}

    public record AddTagRequest(
            @NotBlank String slug,
            @NotBlank @Pattern(regexp = "has|teaches|needs|enjoys") String relation) {}

    private Dtos() {}
}
