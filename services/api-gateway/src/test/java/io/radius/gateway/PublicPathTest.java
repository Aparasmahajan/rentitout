package io.radius.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The edge decides which requests may pass without a token. Prefix matching is
 * easy to get one character too wide, and a path that slips through here only
 * has the service's own check left, so the boundary is pinned directly.
 */
class PublicPathTest {

    @Test
    void the_chat_socket_upgrade_needs_no_header() {
        assertThat(AuthenticationFilter.isPublic("/ws", HttpMethod.GET)).isTrue();
        assertThat(AuthenticationFilter.isPublic("/ws/info", HttpMethod.GET)).isTrue();
    }

    @Test
    void a_path_that_merely_starts_with_ws_is_not_the_socket() {
        assertThat(AuthenticationFilter.isPublic("/wsx", HttpMethod.GET)).isFalse();
        assertThat(AuthenticationFilter.isPublic("/ws-admin", HttpMethod.POST)).isFalse();
    }

    @Test
    void reading_a_listing_is_open_but_changing_one_is_not() {
        assertThat(AuthenticationFilter.isPublic("/api/listings/abc", HttpMethod.GET)).isTrue();
        assertThat(AuthenticationFilter.isPublic("/api/listings/abc", HttpMethod.PATCH)).isFalse();
    }

    @Test
    void requests_always_need_a_token() {
        assertThat(AuthenticationFilter.isPublic("/api/requests", HttpMethod.GET)).isFalse();
    }
}
