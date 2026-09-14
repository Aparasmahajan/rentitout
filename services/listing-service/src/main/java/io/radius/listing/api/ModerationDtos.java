package io.radius.listing.api;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Boundary shapes for comments, ratings, reports and bans. Entities never leave the service layer. */
public final class ModerationDtos {

    // ------------------------------------------------------------- comments

    public record CreateComment(
            @NotBlank @Size(max = 2000) String body,
            /** Null for a top-level comment. A reply to a reply is rejected. */
            UUID parentId) {}

    public record EditComment(@NotBlank @Size(max = 2000) String body) {}

    public record CommentView(
            UUID id,
            UUID listingId,
            UUID authorId,
            String authorName,
            UUID parentId,
            /** Null once removed — the tombstone keeps its place in the thread. */
            String body,
            boolean deleted,
            Instant createdAt,
            Instant editedAt,
            /** What the caller may do, resolved server-side so the client renders no guesses. */
            boolean canEdit,
            boolean canDelete,
            List<CommentView> replies) {}

    // -------------------------------------------------------------- ratings

    public record CreateRating(
            @Min(1) @Max(5) int stars,
            @Size(max = 120) String title,
            @Size(max = 2000) String body) {}

    public record RatingView(
            UUID id,
            UUID listingId,
            UUID authorId,
            String authorName,
            int stars,
            String title,
            String body,
            boolean verifiedBooking,
            Instant createdAt,
            Instant editedAt,
            boolean canEdit,
            boolean canDelete) {}

    public record RatingSummary(
            Double average,
            int count,
            /** Star -> how many. Missing stars are absent, not zero-filled. */
            Map<Integer, Long> histogram,
            /** The caller's own rating, if they have left one. */
            RatingView mine,
            List<RatingView> ratings) {}

    // -------------------------------------------------------------- reports

    public record CreateReport(
            @NotBlank String targetType,
            UUID targetId,
            @NotBlank @Size(max = 60) String reason,
            @Size(max = 1000) String detail) {}

    public record ReportView(
            UUID id,
            UUID reporterId,
            String targetType,
            UUID targetId,
            String reason,
            String detail,
            String state,
            Instant createdAt,
            UUID decidedBy,
            Instant decidedAt,
            String note,
            /** The reported content, inlined so a decision needs no second request. */
            String targetSummary,
            UUID targetAuthorId) {}

    public record Decision(@Size(max = 1000) String note) {}

    // ----------------------------------------------------------------- bans

    public record BanRequest(
            @Min(1) @Max(3650) int days,
            @NotBlank @Size(max = 500) String reason) {}

    public record BanView(UUID userId, Instant bannedUntil, String reason, UUID setBy, Instant setAt) {}

    private ModerationDtos() {}
}
