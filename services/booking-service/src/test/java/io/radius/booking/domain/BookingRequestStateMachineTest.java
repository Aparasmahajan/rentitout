package io.radius.booking.domain;

import io.radius.booking.domain.BookingRequest.Status;
import io.radius.common.web.ApiException;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The state machine is the part of the system where a bug costs someone money
 * or a wasted trip across town, so it is tested directly rather than through
 * the controller.
 */
class BookingRequestStateMachineTest {

    private BookingRequest newRequest() {
        return new BookingRequest(UUID.randomUUID(), "Ladder", UUID.randomUUID(), UUID.randomUUID(),
                LocalDate.now().plusDays(1), LocalDate.now().plusDays(2), 2, "DAY",
                500, 1000, 2000, 0, "EUR", "Need it Saturday", Instant.now().plusSeconds(3600));
    }

    @Test
    void a_new_request_is_sent() {
        assertThat(newRequest().status()).isEqualTo(Status.SENT);
    }

    @Test
    void the_happy_path_runs_end_to_end() {
        BookingRequest request = newRequest();
        request.transitionTo(Status.ACCEPTED);
        request.transitionTo(Status.IN_PROGRESS);
        request.transitionTo(Status.COMPLETED);
        assertThat(request.status()).isEqualTo(Status.COMPLETED);
    }

    @Test
    void a_request_can_be_completed_straight_from_accepted() {
        BookingRequest request = newRequest();
        request.transitionTo(Status.ACCEPTED);
        request.transitionTo(Status.COMPLETED);
        assertThat(request.status()).isEqualTo(Status.COMPLETED);
    }

    @Test
    void a_declined_request_is_final() {
        BookingRequest request = newRequest();
        request.transitionTo(Status.DECLINED);

        assertThatThrownBy(() -> request.transitionTo(Status.ACCEPTED))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("cannot become ACCEPTED");
    }

    @Test
    void a_sent_request_cannot_skip_to_completed() {
        assertThatThrownBy(() -> newRequest().transitionTo(Status.COMPLETED))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void a_completed_request_cannot_be_cancelled() {
        BookingRequest request = newRequest();
        request.transitionTo(Status.ACCEPTED);
        request.transitionTo(Status.COMPLETED);

        assertThat(request.canTransitionTo(Status.CANCELLED)).isFalse();
    }

    @Test
    void the_total_is_rate_times_units_plus_deposit_plus_fee() {
        BookingRequest request = newRequest();
        assertThat(request.getAmountMinor()).isEqualTo(1000);
        assertThat(request.getTotalMinor()).isEqualTo(1000 + 2000 + 0);
    }
}
