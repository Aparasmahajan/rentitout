package io.radius.user.api;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class VerificationDtos {

    // ---- what a member submits --------------------------------------------
    public record SubmitVerification(
            @NotBlank @Pattern(regexp = "IDENTITY|PROFESSIONAL") String kind,
            @Size(max = 8) List<@NotBlank String> evidenceKeys,
            @Size(max = 500) String note) {}

    /** What the member sees about their own request, including why it was refused. */
    public record VerificationStatus(UUID id, String kind, String state, String memberNote,
                                     String decisionNote, Instant submittedAt, Instant decidedAt,
                                     boolean open) {}

    /** The one-glance answer for "where am I up to". */
    public record MyVerification(boolean idChecked, Instant idCheckedAt,
                                 String identityState, String professionalState,
                                 boolean canGoProfessional, boolean isProfessional,
                                 List<VerificationStatus> history) {}

    // ---- professional profile ---------------------------------------------
    public record ProfessionalUpsert(
            @NotBlank @Pattern(regexp = "ELECTRICIAN|PLUMBER|AC_SERVICE|APPLIANCE_REPAIR|CARPENTER|PAINTER|PEST_CONTROL|CLEANING|HOUSE_HELP|COOK|DRIVER|MOVER|GARDENER|BEAUTICIAN|TUTOR|IT_SUPPORT|OTHER")
            String trade,
            @Size(max = 80) String businessName,
            @Size(max = 1000) String about,
            @Size(max = 200) String shopAddress,
            @DecimalMin("-90.0") @DecimalMax("90.0") Double shopLat,
            @DecimalMin("-180.0") @DecimalMax("180.0") Double shopLon,
            @Min(1) @Max(100) Integer serviceRadiusKm,
            @Min(0) @Max(70) Integer yearsExperience,
            @Size(max = 60) String licenceRef,
            @Size(max = 60) String insuranceRef,
            @Size(max = 120) String languages,
            @Size(max = 20) String contactPhone) {}

    public record ProfessionalResponse(UUID userId, String displayName, String trade,
                                       String businessName, String about, String shopAddress,
                                       Double shopLat, Double shopLon, int serviceRadiusKm,
                                       Integer yearsExperience, String licenceRef, String insuranceRef,
                                       String languages, String contactPhone, String state,
                                       boolean idChecked, Instant idCheckedAt, Instant createdAt) {}

    // ---- admin -------------------------------------------------------------
    public record ReviewItem(UUID id, UUID userId, String displayName, String phone, String kind,
                             String state, String memberNote, List<String> evidenceKeys,
                             Instant submittedAt, LocalDate purgeAfter,
                             ProfessionalResponse professional) {}

    public record Decision(@Size(max = 500) String note) {}

    private VerificationDtos() {}
}
