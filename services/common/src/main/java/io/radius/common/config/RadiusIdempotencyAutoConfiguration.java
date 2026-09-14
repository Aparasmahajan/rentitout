package io.radius.common.config;

import io.radius.common.support.IdempotencyGuard;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * Ordered after Redis's own auto-configuration: {@code @ConditionalOnBean} is
 * evaluated in registration order, so without this the StringRedisTemplate does
 * not exist yet and the guard is silently skipped.
 */
@AutoConfiguration(after = RedisAutoConfiguration.class)
@ConditionalOnClass(StringRedisTemplate.class)
public class RadiusIdempotencyAutoConfiguration {

    @Bean
    @ConditionalOnBean(StringRedisTemplate.class)
    @ConditionalOnMissingBean
    public IdempotencyGuard idempotencyGuard(StringRedisTemplate redis) {
        return new IdempotencyGuard(redis);
    }
}
