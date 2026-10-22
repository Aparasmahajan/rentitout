package io.radius.common.support;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The guard claims a key before the consumer's work runs. If the work then
 * fails and the key is not released, Kafka's retry is skipped as a duplicate
 * and the event is silently lost. These pin the claim and the release to the
 * same Redis key.
 */
class IdempotencyGuardTest {

    @SuppressWarnings("unchecked")
    private final ValueOperations<String, String> values = mock(ValueOperations.class);
    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
    private final IdempotencyGuard guard = new IdempotencyGuard(redis);

    IdempotencyGuardTest() {
        when(redis.opsForValue()).thenReturn(values);
    }

    @Test
    void the_first_sighting_claims_the_key() {
        when(values.setIfAbsent(eq("idem:listing.user:e-1"), eq("1"), any(Duration.class))).thenReturn(true);

        assertThat(guard.firstTime("listing.user", "e-1")).isTrue();
    }

    @Test
    void a_replay_is_reported_as_seen() {
        when(values.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(false);

        assertThat(guard.firstTime("listing.user", "e-1")).isFalse();
    }

    @Test
    void forgetting_releases_exactly_the_key_that_was_claimed() {
        guard.forget("listing.user", "e-1");

        verify(redis).delete("idem:listing.user:e-1");
    }
}
