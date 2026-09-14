package io.radius.user.domain;

import io.radius.common.web.ApiException;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * These are the rules that decide whether a stranger gets sent to someone's
 * flat, so they are tested directly rather than through a controller.
 */
class VerificationRequestTest {

    private VerificationRequest identity() {
        return new VerificationRequest(UUID.randomUUID(), VerificationRequest.Kind.IDENTITY,
                "evidence/one.jpg", "Passport photo attached");
    }

    @Test
    void a_new_request_is_open_and_waiting() {
        VerificationRequest request = identity();
        assertThat(request.getState()).isEqualTo("SUBMITTED");
        assertThat(request.isOpen()).isTrue();
        assertThat(request.getPurgeAfter()).isNull();
    }

    @Test
    void approving_records_who_decided_and_when_evidence_should_go() {
        VerificationRequest request = identity();
        UUID admin = UUID.randomUUID();

        request.approve(admin, "Passport matches the profile name");

        assertThat(request.getState()).isEqualTo("APPROVED");
        assertThat(request.getDecidedBy()).isEqualTo(admin);
        assertThat(request.getDecidedAt()).isNotNull();
        // The evidence has a destruction date from the moment it is decided.
        assertThat(request.getPurgeAfter()).isNotNull();
        assertThat(request.isOpen()).isFalse();
    }

    @Test
    void a_rejection_must_say_why() {
        VerificationRequest request = identity();

        assertThatThrownBy(() -> request.reject(UUID.randomUUID(), "  "))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Say why");

        // and it stays open, so the member is not left in limbo
        assertThat(request.isOpen()).isTrue();
    }

    @Test
    void a_decided_request_cannot_be_decided_again() {
        VerificationRequest request = identity();
        request.approve(UUID.randomUUID(), "fine");

        assertThatThrownBy(() -> request.reject(UUID.randomUUID(), "changed my mind"))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("already");
    }

    @Test
    void claiming_marks_it_in_review_so_two_admins_do_not_collide() {
        VerificationRequest request = identity();
        UUID admin = UUID.randomUUID();

        request.claim(admin);

        assertThat(request.getState()).isEqualTo("IN_REVIEW");
        assertThat(request.isOpen()).isTrue();
    }

    @Test
    void withdrawing_closes_it_and_purges_immediately() {
        VerificationRequest request = identity();
        request.withdraw();

        assertThat(request.getState()).isEqualTo("WITHDRAWN");
        assertThat(request.getPurgeAfter()).isNotNull();
        assertThat(request.isOpen()).isFalse();
    }
}
