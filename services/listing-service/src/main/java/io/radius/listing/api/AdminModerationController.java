package io.radius.listing.api;

import io.radius.common.security.AuthUser;
import io.radius.common.web.ApiException;
import io.radius.listing.domain.Report;
import io.radius.listing.service.CommentService;
import io.radius.listing.service.ModerationAdminService;
import io.radius.listing.service.ReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * The moderator's surface. Under {@code /api/moderation} rather than
 * {@code /api/admin}, because the gateway already routes every {@code /api/admin}
 * path to user-service — where the verification queue lives.
 *
 * Every method here opens with {@code me.requireAdmin()}, which throws rather
 * than returning a boolean. A forgotten {@code if} on a boolean fails open;
 * this cannot.
 */
@RestController
@Tag(name = "Moderation", description = "The reports queue, content removal and comment bans")
public class AdminModerationController {

    private final ReportService reports;
    private final CommentService comments;
    private final ModerationAdminService moderation;

    public AdminModerationController(ReportService reports, CommentService comments,
                                     ModerationAdminService moderation) {
        this.reports = reports;
        this.comments = comments;
        this.moderation = moderation;
    }

    // ---- the queue ---------------------------------------------------------

    @GetMapping("/api/moderation/reports")
    @Operation(summary = "Reports across listings, comments and ratings, oldest first")
    public List<ModerationDtos.ReportView> queue(AuthUser me,
                                                 @RequestParam(required = false) String state) {
        me.requireAdmin();
        return reports.queue(state);
    }

    @PostMapping("/api/moderation/reports/{id}/claim")
    @Operation(summary = "Take a report — it moves to REVIEWING so two moderators do not both work it")
    public ModerationDtos.ReportView claim(AuthUser me, @PathVariable UUID id) {
        me.requireAdmin();
        return reports.claim(id, me.id());
    }

    @PostMapping("/api/moderation/reports/{id}/action")
    @Operation(summary = "Uphold a report. Removing the content is a separate, deliberate step.")
    public ModerationDtos.ReportView action(AuthUser me, @PathVariable UUID id,
                                            @Valid @RequestBody ModerationDtos.Decision body) {
        me.requireAdmin();
        return reports.decide(id, Report.State.ACTIONED, me.id(), body.note());
    }

    @PostMapping("/api/moderation/reports/{id}/dismiss")
    @Operation(summary = "Dismiss a report — a refusal carries a reason, as everywhere else here")
    public ModerationDtos.ReportView dismiss(AuthUser me, @PathVariable UUID id,
                                             @Valid @RequestBody ModerationDtos.Decision body) {
        me.requireAdmin();
        if (body.note() == null || body.note().isBlank()) {
            throw ApiException.badRequest("reason_required", "Say why this report was dismissed");
        }
        return reports.decide(id, Report.State.DISMISSED, me.id(), body.note());
    }

    // ---- acting on content -------------------------------------------------

    @DeleteMapping("/api/moderation/comments/{commentId}")
    @Operation(summary = "Remove a comment and close every open report about it")
    public ResponseEntity<Void> removeComment(AuthUser me, @PathVariable UUID commentId,
                                              @RequestParam(required = false) String note) {
        me.requireAdmin();
        moderation.removeComment(commentId, me.id(), note);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/api/moderation/ratings/{ratingId}")
    @Operation(summary = "Remove a rating and close every open report about it")
    public ResponseEntity<Void> removeRating(AuthUser me, @PathVariable UUID ratingId,
                                             @RequestParam(required = false) String note) {
        me.requireAdmin();
        moderation.removeRating(ratingId, me.id(), note);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/api/moderation/listings/{listingId}/unlist")
    @Operation(summary = "Take a listing out of the feed and close every open report about it")
    public ResponseEntity<Void> unlistListing(AuthUser me, @PathVariable UUID listingId,
                                              @Valid @RequestBody ModerationDtos.Decision body) {
        me.requireAdmin();
        moderation.unlistListing(listingId, me.id(), body.note());
        return ResponseEntity.noContent().build();
    }

    // ---- comment bans ------------------------------------------------------

    @GetMapping("/api/moderation/bans")
    @Operation(summary = "Members who cannot comment right now")
    public List<ModerationDtos.BanView> bans(AuthUser me) {
        me.requireAdmin();
        return comments.activeBans();
    }

    @PostMapping("/api/moderation/members/{userId}/comment-ban")
    @Operation(summary = "Stop a member commenting, for a number of days")
    public ModerationDtos.BanView ban(AuthUser me, @PathVariable UUID userId,
                                      @Valid @RequestBody ModerationDtos.BanRequest req) {
        me.requireAdmin();
        return comments.ban(userId, me.id(), req);
    }

    @DeleteMapping("/api/moderation/members/{userId}/comment-ban")
    @Operation(summary = "Lift a ban early — it would otherwise expire on its own")
    public ResponseEntity<Void> liftBan(AuthUser me, @PathVariable UUID userId) {
        me.requireAdmin();
        comments.liftBan(userId);
        return ResponseEntity.noContent().build();
    }
}
