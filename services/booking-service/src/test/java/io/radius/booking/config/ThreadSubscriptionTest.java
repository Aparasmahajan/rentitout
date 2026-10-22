package io.radius.booking.config;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * SUBSCRIBE is authorised by reading the request id out of the destination and
 * checking the caller is a party to it. If parsing were loose, a crafted
 * destination could slip past the check, so anything that is not exactly a
 * thread topic must come back as "no thread" and be refused.
 */
class ThreadSubscriptionTest {

    @Test
    void a_thread_topic_yields_its_request_id() {
        UUID id = UUID.randomUUID();

        assertThat(WebSocketConfig.threadOf("/topic/threads/" + id)).isEqualTo(id);
    }

    @Test
    void anything_else_is_not_a_thread() {
        assertThat(WebSocketConfig.threadOf(null)).isNull();
        assertThat(WebSocketConfig.threadOf("/topic/other")).isNull();
        assertThat(WebSocketConfig.threadOf("/topic/threads/")).isNull();
        assertThat(WebSocketConfig.threadOf("/topic/threads/not-a-uuid")).isNull();
        assertThat(WebSocketConfig.threadOf("/topic/threads/" + UUID.randomUUID() + "/extra")).isNull();
    }
}
