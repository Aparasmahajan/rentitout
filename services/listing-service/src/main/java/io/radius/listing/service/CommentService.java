package io.radius.listing.service;

import io.radius.common.web.ApiException;
import io.radius.listing.api.ModerationDtos;
import io.radius.listing.domain.CommentBan;
import io.radius.listing.domain.Listing;
import io.radius.listing.domain.ListingComment;
import io.radius.listing.repo.CommentBanRepository;
import io.radius.listing.repo.ListingCommentRepository;
import io.radius.listing.repo.ListingRepository;
import io.radius.listing.repo.MemberLocationRepository;
import io.radius.listing.support.PostingLimiter;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Comments on a listing, threaded one level deep.
 *
 * Three rules the code is built around:
 *   - a ban is checked at post time, not render time, so an existing thread
 *     survives its author being silenced;
 *   - deletion is soft, because moderation without evidence is guesswork;
 *   - who may edit or delete is resolved here and sent to the client as two
 *     booleans, so no client re-derives the rule and gets it wrong.
 */
@Service
public class CommentService {

    private final ListingCommentRepository comments;
    private final ListingRepository listings;
    private final CommentBanRepository bans;
    private final MemberLocationRepository members;
    private final PostingLimiter limiter;

    public CommentService(ListingCommentRepository comments, ListingRepository listings,
                          CommentBanRepository bans, MemberLocationRepository members,
                          PostingLimiter limiter) {
        this.comments = comments;
        this.listings = listings;
        this.bans = bans;
        this.members = members;
        this.limiter = limiter;
    }

    @Transactional
    public ModerationDtos.CommentView post(UUID listingId, UUID authorId, ModerationDtos.CreateComment req) {
        Listing listing = listings.findById(listingId)
                .orElseThrow(() -> ApiException.notFound("Listing"));

        requireNotBanned(authorId);
        // Ban first, then rate: a banned member should hear that they are
        // banned, not that they are posting too fast.
        limiter.checkComment(authorId);

        UUID parentId = req.parentId();
        if (parentId != null) {
            ListingComment parent = comments.findById(parentId)
                    .orElseThrow(() -> ApiException.notFound("Parent comment"));
            if (!parent.getListingId().equals(listingId)) {
                throw ApiException.badRequest("parent_mismatch", "That comment is on a different listing");
            }
            // One level only. Replying to a reply attaches to its parent instead
            // of rejecting outright — the member's intent is obvious and a error
            // here would be pedantry.
            if (parent.getParentId() != null) parentId = parent.getParentId();
        }

        ListingComment saved = comments.save(
                new ListingComment(listing.getId(), authorId, parentId, req.body().trim()));
        return view(saved, authorId, listing.getOwnerId(), nameOf(authorId), List.of());
    }

    @Transactional
    public ModerationDtos.CommentView edit(UUID commentId, UUID callerId, ModerationDtos.EditComment req) {
        ListingComment comment = load(commentId);
        if (!comment.getAuthorId().equals(callerId)) {
            throw ApiException.forbidden("Only the author may edit a comment");
        }
        if (comment.isDeleted()) {
            throw ApiException.conflict("comment_deleted", "A removed comment cannot be edited");
        }
        requireNotBanned(callerId);

        comment.edit(req.body().trim());
        UUID owner = listings.findById(comment.getListingId()).map(Listing::getOwnerId).orElse(null);
        return view(comment, callerId, owner, nameOf(comment.getAuthorId()), List.of());
    }

    /**
     * Author, listing owner or admin. The owner is included deliberately: they
     * carry the reputational cost of what sits under their listing, so they get
     * to remove it without waiting for a moderator.
     */
    @Transactional
    public void delete(UUID commentId, UUID callerId, boolean isAdmin) {
        ListingComment comment = load(commentId);
        UUID owner = listings.findById(comment.getListingId()).map(Listing::getOwnerId).orElse(null);

        boolean mayDelete = isAdmin
                || comment.getAuthorId().equals(callerId)
                || (owner != null && owner.equals(callerId));
        if (!mayDelete) throw ApiException.forbidden("You cannot remove that comment");

        comment.delete(callerId);
    }

    /** The whole thread, nested one level, newest replies under their parent. */
    @Transactional(readOnly = true)
    public List<ModerationDtos.CommentView> thread(UUID listingId, UUID callerId, boolean isAdmin) {
        UUID owner = listings.findById(listingId).map(Listing::getOwnerId).orElse(null);
        List<ListingComment> all = comments.findByListingIdOrderByCreatedAtAsc(listingId);

        Map<UUID, String> names = new HashMap<>();
        all.forEach(c -> names.computeIfAbsent(c.getAuthorId(), this::nameOf));

        Map<UUID, List<ListingComment>> repliesByParent = new HashMap<>();
        List<ListingComment> roots = new ArrayList<>();
        for (ListingComment c : all) {
            if (c.getParentId() == null) roots.add(c);
            else repliesByParent.computeIfAbsent(c.getParentId(), k -> new ArrayList<>()).add(c);
        }

        List<ModerationDtos.CommentView> out = new ArrayList<>(roots.size());
        for (ListingComment root : roots) {
            List<ModerationDtos.CommentView> replies = repliesByParent
                    .getOrDefault(root.getId(), List.of()).stream()
                    .map(r -> view(r, callerId, owner, names.get(r.getAuthorId()), List.of(), isAdmin))
                    .toList();
            out.add(view(root, callerId, owner, names.get(root.getAuthorId()), replies, isAdmin));
        }
        return out;
    }

    // ------------------------------------------------------------------ bans

    /**
     * An expired row is history, not a ban — so this reads the timestamp rather
     * than the existence of the row, and a ban lifts itself.
     */
    private void requireNotBanned(UUID userId) {
        bans.findById(userId).filter(CommentBan::isActive).ifPresent(ban -> {
            throw new ApiException(org.springframework.http.HttpStatus.FORBIDDEN, "comment_banned",
                    "You cannot comment until " + ban.getBannedUntil().truncatedTo(ChronoUnit.MINUTES)
                            + " — " + ban.getReason());
        });
    }

    @Transactional
    public ModerationDtos.BanView ban(UUID userId, UUID adminId, ModerationDtos.BanRequest req) {
        Instant until = Instant.now().plus(req.days(), ChronoUnit.DAYS);
        CommentBan ban = bans.findById(userId)
                .map(existing -> { existing.extend(until, req.reason(), adminId); return existing; })
                .orElseGet(() -> bans.save(new CommentBan(userId, until, req.reason(), adminId)));
        return new ModerationDtos.BanView(ban.getUserId(), ban.getBannedUntil(), ban.getReason(),
                ban.getSetBy(), ban.getSetAt());
    }

    @Transactional
    public void liftBan(UUID userId) {
        bans.findById(userId).ifPresent(bans::delete);
    }

    @Transactional(readOnly = true)
    public List<ModerationDtos.BanView> activeBans() {
        return bans.findByBannedUntilAfterOrderBySetAtDesc(Instant.now()).stream()
                .map(b -> new ModerationDtos.BanView(b.getUserId(), b.getBannedUntil(), b.getReason(),
                        b.getSetBy(), b.getSetAt()))
                .toList();
    }

    // --------------------------------------------------------------- helpers

    ListingComment load(UUID id) {
        return comments.findById(id).orElseThrow(() -> ApiException.notFound("Comment"));
    }

    private String nameOf(UUID userId) {
        return members.findById(userId).map(m -> m.getDisplayName()).orElse("A neighbour");
    }

    private ModerationDtos.CommentView view(ListingComment c, UUID callerId, UUID listingOwner,
                                            String authorName, List<ModerationDtos.CommentView> replies) {
        return view(c, callerId, listingOwner, authorName, replies, false);
    }

    private ModerationDtos.CommentView view(ListingComment c, UUID callerId, UUID listingOwner,
                                            String authorName, List<ModerationDtos.CommentView> replies,
                                            boolean isAdmin) {
        boolean mine = callerId != null && c.getAuthorId().equals(callerId);
        boolean ownsListing = callerId != null && listingOwner != null && listingOwner.equals(callerId);
        return new ModerationDtos.CommentView(
                c.getId(), c.getListingId(), c.getAuthorId(),
                c.isDeleted() ? null : authorName,
                c.getParentId(), c.getBody(), c.isDeleted(),
                c.getCreatedAt(), c.getEditedAt(),
                mine && !c.isDeleted(),
                !c.isDeleted() && (mine || ownsListing || isAdmin),
                replies);
    }
}
