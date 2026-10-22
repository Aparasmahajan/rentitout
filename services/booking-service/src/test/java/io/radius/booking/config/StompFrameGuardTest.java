package io.radius.booking.config;

import org.junit.jupiter.api.Test;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.security.access.AccessDeniedException;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The chat socket is listen-only. A client SEND to a thread topic used to reach
 * the simple broker untouched and arrive at both parties as chat, with or
 * without a CONNECT first. These pin the allow-list so that cannot come back.
 */
class StompFrameGuardTest {

    // Neither collaborator is reached on the paths tested here.
    private final WebSocketConfig config = new WebSocketConfig(null, null);

    @Test
    void a_client_cannot_send_into_a_thread() {
        StompHeaderAccessor send = StompHeaderAccessor.create(StompCommand.SEND);
        send.setDestination("/topic/threads/" + UUID.randomUUID());

        assertThatThrownBy(() -> config.inspect(send)).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void nor_to_an_application_destination() {
        StompHeaderAccessor send = StompHeaderAccessor.create(StompCommand.SEND);
        send.setDestination("/app/anything");

        assertThatThrownBy(() -> config.inspect(send)).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void the_stomp_spelling_of_connect_still_needs_a_token() {
        StompHeaderAccessor stomp = StompHeaderAccessor.create(StompCommand.STOMP);

        assertThatThrownBy(() -> config.inspect(stomp)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void a_subscribe_without_a_signed_in_socket_is_refused() {
        StompHeaderAccessor subscribe = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        subscribe.setDestination("/topic/threads/" + UUID.randomUUID());

        assertThatThrownBy(() -> config.inspect(subscribe)).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void leaving_is_always_allowed() {
        assertThatCode(() -> config.inspect(StompHeaderAccessor.create(StompCommand.UNSUBSCRIBE)))
                .doesNotThrowAnyException();
        assertThatCode(() -> config.inspect(StompHeaderAccessor.create(StompCommand.DISCONNECT)))
                .doesNotThrowAnyException();
    }
}
