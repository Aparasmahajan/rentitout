package io.radius.listing.domain;

import io.radius.common.web.ApiException;
import io.radius.listing.domain.Report.State;
import io.radius.listing.domain.Report.TargetType;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The moderation rules that are easy to get wrong and expensive when they are:
 * a report that can be decided twice, a comment whose body survives its
 * removal, a ban that never lifts.
 */
class ModerationDomainTest {

    private Report newReport() {
        return new Report(UUID.randomUUID(), TargetType.COMMENT, UUID.randomUUID(), "SPAM", "Third one today");
    }

    // ---- the report state machine ------------------------------------------

    @Test
    void a_new_report_is_open() {
        assertThat(newReport().getState()).isEqualTo(State.OPEN);
        assertThat(newReport().isOpen()).isTrue();
    }

    @Test
    void a_report_can_be_claimed_then_decided() {
        Report report = newReport();
        UUID admin = UUID.randomUUID();

        report.moveTo(State.REVIEWING, admin, null);
        report.moveTo(State.ACTIONED, admin, "Removed the comment");

        assertThat(report.getState()).isEqualTo(State.ACTIONED);
        assertThat(report.getDecidedBy()).isEqualTo(admin);
        assertThat(report.getDecidedAt()).isNotNull();
        assertThat(report.getNote()).isEqualTo("Removed the comment");
        assertThat(report.isOpen()).isFalse();
    }

    @Test
    void an_obvious_report_can_be_actioned_without_being_claimed() {
        Report report = newReport();
        report.moveTo(State.ACTIONED, UUID.randomUUID(), "Clear spam");
        assertThat(report.getState()).isEqualTo(State.ACTIONED);
    }

    @Test
    void a_decided_report_cannot_be_decided_again() {
        Report report = newReport();
        report.moveTo(State.DISMISSED, UUID.randomUUID(), "Nothing wrong with it");

        assertThatThrownBy(() -> report.moveTo(State.ACTIONED, UUID.randomUUID(), "Changed my mind"))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("DISMISSED");
    }

    @Test
    void claiming_does_not_record_a_decision() {
        Report report = newReport();
        report.moveTo(State.REVIEWING, UUID.randomUUID(), "looking");

        assertThat(report.getDecidedBy()).isNull();
        assertThat(report.getDecidedAt()).isNull();
        // A note only means something once a decision is made.
        assertThat(report.getNote()).isNull();
    }

    // ---- comments ----------------------------------------------------------

    @Test
    void a_removed_comment_keeps_its_row_and_loses_its_body() {
        ListingComment comment = new ListingComment(UUID.randomUUID(), UUID.randomUUID(), null, "Buy my thing");
        UUID moderator = UUID.randomUUID();

        comment.delete(moderator);

        assertThat(comment.isDeleted()).isTrue();
        assertThat(comment.getBody()).isNull();
        assertThat(comment.getId()).isNotNull();
    }

    @Test
    void deleting_twice_keeps_the_first_decision() {
        ListingComment comment = new ListingComment(UUID.randomUUID(), UUID.randomUUID(), null, "Spam");
        comment.delete(UUID.randomUUID());
        Instant first = comment.getDeletedAt();

        comment.delete(UUID.randomUUID());

        assertThat(comment.getDeletedAt()).isEqualTo(first);
    }

    @Test
    void editing_a_comment_marks_it_edited() {
        ListingComment comment = new ListingComment(UUID.randomUUID(), UUID.randomUUID(), null, "Does it fold?");
        assertThat(comment.getEditedAt()).isNull();

        comment.edit("Does it fold to 1.8 m?");

        assertThat(comment.getBody()).isEqualTo("Does it fold to 1.8 m?");
        assertThat(comment.getEditedAt()).isNotNull();
    }

    // ---- ratings -----------------------------------------------------------

    @Test
    void a_rating_without_a_request_is_not_a_verified_booking() {
        ListingRating open = new ListingRating(UUID.randomUUID(), UUID.randomUUID(), (short) 4, null, null, null);
        ListingRating booked = new ListingRating(UUID.randomUUID(), UUID.randomUUID(), (short) 5, null, null,
                UUID.randomUUID());

        assertThat(open.isVerifiedBooking()).isFalse();
        assertThat(booked.isVerifiedBooking()).isTrue();
    }

    @Test
    void a_removed_rating_shows_neither_title_nor_body() {
        ListingRating rating = new ListingRating(UUID.randomUUID(), UUID.randomUUID(), (short) 1,
                "Terrible", "Call this number instead", null);

        rating.delete(UUID.randomUUID());

        assertThat(rating.getTitle()).isNull();
        assertThat(rating.getBody()).isNull();
        // The stars stay readable: the aggregate excludes deleted rows by
        // query, not by hiding the number here.
        assertThat(rating.getStars()).isEqualTo((short) 1);
    }

    @Test
    void removing_your_own_rating_is_not_the_same_as_being_moderated() {
        UUID author = UUID.randomUUID();
        ListingRating mine = new ListingRating(UUID.randomUUID(), author, (short) 3, null, null, null);
        ListingRating moderated = new ListingRating(UUID.randomUUID(), author, (short) 1, null, null, null);

        mine.delete(author);
        moderated.delete(UUID.randomUUID());

        // The difference is what decides whether they may rate again: the unique
        // index allows one row each, so a self-removal has to be reusable.
        assertThat(mine.wasSelfRemoved()).isTrue();
        assertThat(moderated.wasSelfRemoved()).isFalse();
    }

    @Test
    void a_restored_rating_comes_back_with_the_new_content() {
        UUID author = UUID.randomUUID();
        ListingRating rating = new ListingRating(UUID.randomUUID(), author, (short) 2, "Meh", "Not great", null);
        rating.delete(author);

        UUID request = UUID.randomUUID();
        rating.restore((short) 5, "Actually good", "Second time was better", request);

        assertThat(rating.isDeleted()).isFalse();
        assertThat(rating.getStars()).isEqualTo((short) 5);
        assertThat(rating.getTitle()).isEqualTo("Actually good");
        assertThat(rating.isVerifiedBooking()).isTrue();
        // Not an edit of the old opinion — a new one, so it carries no "edited" mark.
        assertThat(rating.getEditedAt()).isNull();
    }

    // ---- comment bans ------------------------------------------------------

    @Test
    void a_ban_lifts_itself_when_it_expires() {
        UUID member = UUID.randomUUID();
        CommentBan live = new CommentBan(member, Instant.now().plus(1, ChronoUnit.DAYS), "Spam",
                UUID.randomUUID());
        CommentBan expired = new CommentBan(member, Instant.now().minus(1, ChronoUnit.SECONDS), "Spam",
                UUID.randomUUID());

        assertThat(live.isActive()).isTrue();
        // The row stays for the history; it just stops biting.
        assertThat(expired.isActive()).isFalse();
    }

    @Test
    void extending_a_ban_moves_the_date_and_records_who() {
        CommentBan ban = new CommentBan(UUID.randomUUID(), Instant.now().plus(1, ChronoUnit.DAYS), "Spam",
                UUID.randomUUID());
        UUID secondAdmin = UUID.randomUUID();
        Instant longer = Instant.now().plus(30, ChronoUnit.DAYS);

        ban.extend(longer, "Did it again", secondAdmin);

        assertThat(ban.getBannedUntil()).isEqualTo(longer);
        assertThat(ban.getReason()).isEqualTo("Did it again");
        assertThat(ban.getSetBy()).isEqualTo(secondAdmin);
    }
}
