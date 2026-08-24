package io.radius.common.security;

import io.radius.common.web.ApiException;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AuthUserTest {

    @Test
    void a_caller_with_no_stated_role_is_an_ordinary_member() {
        AuthUser user = new AuthUser(UUID.randomUUID(), "Amara");
        assertThat(user.role()).isEqualTo(AuthUser.MEMBER);
        assertThat(user.isAdmin()).isFalse();
    }

    @Test
    void requireAdmin_throws_for_a_member_rather_than_returning_false() {
        AuthUser member = new AuthUser(UUID.randomUUID(), "Amara", AuthUser.MEMBER);

        // Failing closed matters here: a forgotten `if` on a boolean would open
        // the review queue to everyone.
        assertThatThrownBy(member::requireAdmin)
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("administrator");
    }

    @Test
    void requireAdmin_passes_for_an_admin() {
        AuthUser admin = new AuthUser(UUID.randomUUID(), "Paras", AuthUser.ADMIN);
        assertThat(admin.isAdmin()).isTrue();
        admin.requireAdmin();
    }

    @Test
    void an_unknown_role_string_is_not_an_admin() {
        AuthUser odd = new AuthUser(UUID.randomUUID(), "Someone", "SUPERUSER");
        assertThat(odd.isAdmin()).isFalse();
    }
}
