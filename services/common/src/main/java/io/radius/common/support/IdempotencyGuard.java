package io.radius.common.support;

import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;

/**
 * Kafka delivery is at-least-once, so a consumer that mutates state has to be
 * able to say "seen this one". Redis SETNX with a TTL is enough here; a
 * financial consumer should use a uniqueness constraint in its own database
 * instead — see payment-service, which keys on the gateway reference.
 */
public class IdempotencyGuard {

    private static final Duration DEFAULT_TTL = Duration.ofDays(3);

    private final StringRedisTemplate redis;

    public IdempotencyGuard(StringRedisTemplate redis) {
        this.redis = redis;
    }

    /** True the first time this key is seen, false on every replay. */
    public boolean firstTime(String scope, String key) {
        return firstTime(scope, key, DEFAULT_TTL);
    }

    public boolean firstTime(String scope, String key, Duration ttl) {
        Boolean set = redis.opsForValue().setIfAbsent("idem:" + scope + ":" + key, "1", ttl);
        return Boolean.TRUE.equals(set);
    }
}
