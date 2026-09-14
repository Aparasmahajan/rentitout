package io.radius.listing.service;

import io.radius.common.web.ApiException;
import io.radius.listing.api.ModerationDtos;
import io.radius.listing.domain.Listing;
import io.radius.listing.domain.ListingComment;
import io.radius.listing.domain.ListingRating;
import io.radius.listing.domain.Report;
import io.radius.listing.repo.ListingCommentRepository;
import io.radius.listing.repo.ListingRatingRepository;
import io.radius.listing.repo.ListingRepository;
import io.radius.listing.repo.ReportRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Reports against anything reportable, in one table.
 *
 * The queue inlines whatever was reported, because a moderator deciding with a
 * second tab open decides slower and worse — and by the time they look, the
 * author may have edited it.
 */
@Service
public class ReportService {

    /**
     * A fixed list plus free text. Fixed reasons are what make a queue
     * sortable; free text is what makes an unusual report reportable at all.
     */
    public static final Set<String> REASONS = Set.of(
            "SPAM", "SCAM", "OFFENSIVE", "WRONG_CATEGORY", "PROHIBITED_ITEM",
            "HARASSMENT", "MISLEADING", "OTHER");

    private final ReportRepository reports;
    private final ListingRepository listings;
    private final ListingCommentRepository comments;
    private final ListingRatingRepository ratings;

    public ReportService(ReportRepository reports, ListingRepository listings,
                         ListingCommentRepository comments, ListingRatingRepository ratings) {
        this.reports = reports;
        this.listings = listings;
        this.comments = comments;
        this.ratings = ratings;
    }

    @Transactional
    public ModerationDtos.ReportView create(UUID reporterId, ModerationDtos.CreateReport req) {
        Report.TargetType type = parseType(req.targetType());
        String reason = req.reason().trim().toUpperCase();
        if (!REASONS.contains(reason)) {
            throw ApiException.badRequest("bad_reason", "Pick one of: " + String.join(", ", REASONS));
        }
        if (req.targetId() == null) {
            throw ApiException.badRequest("target_required", "Say what is being reported");
        }
        requireTargetExists(type, req.targetId());

        // Reporting the same thing twice is the same report. Returning the
        // original rather than erroring means the member sees "reported" either
        // way, and the queue does not fill with duplicates.
        Report existing = reports
                .findByReporterIdAndTargetTypeAndTargetId(reporterId, type.name(), req.targetId())
                .orElse(null);
        if (existing != null) return view(existing);

        Report report = reports.save(new Report(reporterId, type, req.targetId(), reason,
                req.detail() == null || req.detail().isBlank() ? null : req.detail().trim()));
        return view(report);
    }

    /** The whole queue, or one state of it. Oldest first — a report left waiting is the one that matters. */
    @Transactional(readOnly = true)
    public List<ModerationDtos.ReportView> queue(String state) {
        List<Report> rows = state == null || state.isBlank()
                ? reports.findAllByOrderByCreatedAtAsc()
                : reports.findByStateOrderByCreatedAtAsc(parseState(state).name());
        return rows.stream().map(this::view).toList();
    }

    @Transactional
    public ModerationDtos.ReportView claim(UUID reportId, UUID adminId) {
        return decide(reportId, Report.State.REVIEWING, adminId, null);
    }

    @Transactional
    public ModerationDtos.ReportView decide(UUID reportId, Report.State next, UUID adminId, String note) {
        Report report = reports.findById(reportId).orElseThrow(() -> ApiException.notFound("Report"));
        // The entity owns the transition table; this only says who and why.
        report.moveTo(next, adminId, note);
        return view(report);
    }

    /** Every open report about one thing, so acting on content closes them together. */
    @Transactional
    public int closeAllFor(Report.TargetType type, UUID targetId, UUID adminId, String note) {
        List<Report> open = reports.findByTargetTypeAndTargetIdOrderByCreatedAtAsc(type.name(), targetId)
                .stream().filter(Report::isOpen).toList();
        open.forEach(r -> r.moveTo(Report.State.ACTIONED, adminId, note));
        return open.size();
    }

    // --------------------------------------------------------------- helpers

    private void requireTargetExists(Report.TargetType type, UUID targetId) {
        boolean exists = switch (type) {
            case LISTING -> listings.existsById(targetId);
            case COMMENT -> comments.existsById(targetId);
            case RATING -> ratings.existsById(targetId);
        };
        if (!exists) throw ApiException.notFound(switch (type) {
            case LISTING -> "Listing";
            case COMMENT -> "Comment";
            case RATING -> "Rating";
        });
    }

    private ModerationDtos.ReportView view(Report r) {
        String summary = null;
        UUID author = null;

        switch (r.getTargetType()) {
            case LISTING -> {
                Listing l = listings.findById(r.getTargetId()).orElse(null);
                if (l != null) {
                    summary = l.getTitle();
                    author = l.getOwnerId();
                }
            }
            case COMMENT -> {
                ListingComment c = comments.findById(r.getTargetId()).orElse(null);
                if (c != null) {
                    summary = c.isDeleted() ? "(removed)" : c.getBody();
                    author = c.getAuthorId();
                }
            }
            case RATING -> {
                ListingRating rating = ratings.findById(r.getTargetId()).orElse(null);
                if (rating != null) {
                    summary = rating.isDeleted() ? "(removed)" : ratingSummary(rating);
                    author = rating.getAuthorId();
                }
            }
        }

        return new ModerationDtos.ReportView(r.getId(), r.getReporterId(), r.getTargetType().name(),
                r.getTargetId(), r.getReason(), r.getDetail(), r.getState().name(), r.getCreatedAt(),
                r.getDecidedBy(), r.getDecidedAt(), r.getNote(), summary, author);
    }

    /** Stars, headline and text on one line — enough to decide without opening the listing. */
    private static String ratingSummary(ListingRating rating) {
        StringBuilder text = new StringBuilder(rating.getStars() + "★");
        if (rating.getTitle() != null) text.append(' ').append(rating.getTitle());
        if (rating.getBody() != null) text.append(" — ").append(rating.getBody());
        return text.toString();
    }

    private static Report.TargetType parseType(String raw) {
        try {
            return Report.TargetType.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest("bad_target_type", "Report a LISTING, COMMENT or RATING");
        }
    }

    private static Report.State parseState(String raw) {
        try {
            return Report.State.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest("bad_state", "No such report state: " + raw);
        }
    }
}
