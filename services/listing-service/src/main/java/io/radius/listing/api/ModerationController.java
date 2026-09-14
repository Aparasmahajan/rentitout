package io.radius.listing.api;

import io.radius.common.security.AuthUser;
import io.radius.listing.service.CommentService;
import io.radius.listing.service.RatingService;
import io.radius.listing.service.ReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.Nullable;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * What a member — or a guest, reading — can do around a listing that is not the
 * listing itself: comment, rate, report.
 *
 * Reading is open, exactly like the listing it hangs off. Writing is not.
 */
@RestController
@Tag(name = "Comments, ratings and reports", description = "The conversation around a listing")
public class ModerationController {

    private final CommentService comments;
    private final RatingService ratings;
    private final ReportService reports;

    public ModerationController(CommentService comments, RatingService ratings, ReportService reports) {
        this.comments = comments;
        this.ratings = ratings;
        this.reports = reports;
    }

    // ---- comments ----------------------------------------------------------

    @GetMapping("/api/listings/{id}/comments")
    @Operation(summary = "The comment thread, nested one level")
    public List<ModerationDtos.CommentView> thread(@Nullable AuthUser me, @PathVariable UUID id) {
        return comments.thread(id, me == null ? null : me.id(), me != null && me.isAdmin());
    }

    @PostMapping("/api/listings/{id}/comments")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Comment on a listing, or reply to a comment on it")
    public ModerationDtos.CommentView comment(AuthUser me, @PathVariable UUID id,
                                              @Valid @RequestBody ModerationDtos.CreateComment req) {
        return comments.post(id, me.id(), req);
    }

    @PatchMapping("/api/comments/{commentId}")
    @Operation(summary = "Edit your own comment")
    public ModerationDtos.CommentView editComment(AuthUser me, @PathVariable UUID commentId,
                                                  @Valid @RequestBody ModerationDtos.EditComment req) {
        return comments.edit(commentId, me.id(), req);
    }

    @DeleteMapping("/api/comments/{commentId}")
    @Operation(summary = "Remove a comment — yours, or one on your listing")
    public ResponseEntity<Void> deleteComment(AuthUser me, @PathVariable UUID commentId) {
        comments.delete(commentId, me.id(), me.isAdmin());
        return ResponseEntity.noContent().build();
    }

    // ---- ratings -----------------------------------------------------------

    @GetMapping("/api/listings/{id}/ratings")
    @Operation(summary = "Average, histogram and every rating, verified bookings first")
    public ModerationDtos.RatingSummary ratingSummary(@Nullable AuthUser me, @PathVariable UUID id) {
        return ratings.summary(id, me == null ? null : me.id(), me != null && me.isAdmin());
    }

    @PostMapping("/api/listings/{id}/ratings")
    @Operation(summary = "Rate a listing, or change the rating you already left")
    public ModerationDtos.RatingView rate(AuthUser me, @PathVariable UUID id,
                                          @Valid @RequestBody ModerationDtos.CreateRating req) {
        return ratings.rate(id, me.id(), req);
    }

    @DeleteMapping("/api/ratings/{ratingId}")
    @Operation(summary = "Remove your own rating")
    public ResponseEntity<Void> deleteRating(AuthUser me, @PathVariable UUID ratingId) {
        ratings.delete(ratingId, me.id(), me.isAdmin());
        return ResponseEntity.noContent().build();
    }

    // ---- reports -----------------------------------------------------------

    @GetMapping("/api/reports/reasons")
    @Operation(summary = "The fixed reasons a report may carry")
    public Set<String> reasons() {
        return ReportService.REASONS;
    }

    @PostMapping("/api/reports")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Report a listing, a comment or a rating")
    public ModerationDtos.ReportView report(AuthUser me, @Valid @RequestBody ModerationDtos.CreateReport req) {
        return reports.create(me.id(), req);
    }
}
