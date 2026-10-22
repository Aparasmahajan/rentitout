package io.radius.listing.domain;

import io.radius.common.web.ApiException;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Pause and resume are the owner's switch on a live listing. Before this rule,
 * "resume" set LIVE from any state, so an owner could put a listing a moderator
 * had taken down straight back into everyone's feed.
 */
class ListingStatusTest {

    private Listing newListing() {
        return new Listing(UUID.randomUUID(), Listing.Kind.RENT_ITEM, "Aluminium ladder, 3 m", 52.5, 13.42);
    }

    @Test
    void a_new_listing_is_live() {
        assertThat(newListing().isLive()).isTrue();
    }

    @Test
    void a_live_listing_can_be_paused_and_resumed() {
        Listing listing = newListing();

        listing.pause();
        assertThat(listing.getStatus()).isEqualTo("PAUSED");

        listing.resume();
        assertThat(listing.getStatus()).isEqualTo("LIVE");
    }

    @Test
    void asking_for_the_state_it_is_already_in_is_a_no_op() {
        Listing listing = newListing();

        listing.resume();
        assertThat(listing.getStatus()).isEqualTo("LIVE");

        listing.pause();
        listing.pause();
        assertThat(listing.getStatus()).isEqualTo("PAUSED");
    }

    @Test
    void an_unlisted_listing_cannot_be_resumed() {
        Listing listing = newListing();
        listing.unlist();

        assertThatThrownBy(listing::resume)
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.code()).isEqualTo("listing_unlisted"));
        assertThat(listing.getStatus()).isEqualTo("UNLISTED");
    }

    @Test
    void an_unlisted_listing_cannot_be_paused_either() {
        Listing listing = newListing();
        listing.unlist();

        assertThatThrownBy(listing::pause)
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.code()).isEqualTo("listing_unlisted"));
    }

    @Test
    void a_paused_listing_can_still_be_unlisted() {
        Listing listing = newListing();
        listing.pause();

        listing.unlist();

        assertThat(listing.getStatus()).isEqualTo("UNLISTED");
    }
}
