package io.radius.gateway;

import io.radius.common.security.AuthUser;
import io.radius.common.security.JwtService;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import reactor.core.publisher.Mono;

/**
 * Rate limit by member when we know who they are, by IP when we do not. Both
 * buckets live in Redis so every gateway replica shares the count.
 */
@Configuration
public class RateLimiterConfig {

    private final JwtService jwt;

    public RateLimiterConfig(JwtService jwt) {
        this.jwt = jwt;
    }

    /**
     * Primary because the gateway's own RequestRateLimiter factory autowires a
     * single KeyResolver, and there are two here. The routes that want the other
     * one name it explicitly in their filter configuration.
     */
    @Bean
    @Primary
    public KeyResolver principalKeyResolver() {
        return exchange -> {
            String header = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
            if (header != null && header.startsWith("Bearer ")) {
                AuthUser user = jwt.verifyAccess(header.substring(7));
                if (user != null) return Mono.just("user:" + user.id());
            }
            var remote = exchange.getRequest().getRemoteAddress();
            return Mono.just("ip:" + (remote == null ? "unknown" : remote.getAddress().getHostAddress()));
        };
    }

    /** OTP is the expensive, abusable endpoint — it gets its own, much tighter bucket. */
    @Bean
    public KeyResolver otpKeyResolver() {
        return exchange -> {
            var remote = exchange.getRequest().getRemoteAddress();
            return Mono.just("otp:" + (remote == null ? "unknown" : remote.getAddress().getHostAddress()));
        };
    }
}
