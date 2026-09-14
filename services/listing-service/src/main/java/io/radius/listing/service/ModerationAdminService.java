package io.radius.listing.service;

import io.radius.listing.domain.Report;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Acting on reported content is two writes — remove the thing, close the
 * reports about it — and they belong in one transaction. Split across two
 * calls from the controller, a failure between them leaves a removed comment
 * with an open report still sitting in the queue, and the next moderator works
 * a decision that has already been made.
 */
@Service
public class ModerationAdminService {

    private final CommentService comments;
    private final RatingService ratings;
    private final ListingService listings;
    private final ReportService reports;

    public ModerationAdminService(CommentService comments, RatingService ratings,
                                  ListingService listings, ReportService reports) {
        this.comments = comments;
        this.ratings = ratings;
        this.listings = listings;
        this.reports = reports;
    }

    @Transactional
    public void removeComment(UUID commentId, UUID adminId, String note) {
        comments.delete(commentId, adminId, true);
        reports.closeAllFor(Report.TargetType.COMMENT, commentId, adminId, note);
    }

    @Transactional
    public void removeRating(UUID ratingId, UUID adminId, String note) {
        ratings.delete(ratingId, adminId, true);
        reports.closeAllFor(Report.TargetType.RATING, ratingId, adminId, note);
    }

    @Transactional
    public void unlistListing(UUID listingId, UUID adminId, String note) {
        listings.unlistByModerator(listingId, note);
        reports.closeAllFor(Report.TargetType.LISTING, listingId, adminId, note);
    }
}
