package io.radius.user.service;

import io.radius.common.web.ApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;

/**
 * OTP lives in Redis, never in Postgres — it is short-lived, high-churn and
 * worthless five minutes later. The code itself is stored, not hashed: it is
 * six digits with a five minute TTL, and a hash of six digits is not a secret.
 * Brute force is stopped by the attempt counter and the gateway's IP bucket.
 */
@Service
public class OtpService {

    private static final Logger log = LoggerFactory.getLogger(OtpService.class);
    private static final Duration TTL = Duration.ofMinutes(5);
    private static final int MAX_ATTEMPTS = 5;
    private static final int MAX_SENDS_PER_HOUR = 5;

    private final StringRedisTemplate redis;
    private final SecureRandom random = new SecureRandom();
    private final boolean exposeCode;

    public OtpService(StringRedisTemplate redis,
                      @Value("${radius.otp.expose-code:false}") boolean exposeCode) {
        this.redis = redis;
        this.exposeCode = exposeCode;
    }

    public record Issued(String code, int expiresInSeconds) {}

    public Issued start(String phone) {
        String sendKey = "otp:sends:" + phone;
        Long sends = redis.opsForValue().increment(sendKey);
        if (sends != null && sends == 1L) redis.expire(sendKey, Duration.ofHours(1));
        if (sends != null && sends > MAX_SENDS_PER_HOUR) {
            throw new ApiException(org.springframework.http.HttpStatus.TOO_MANY_REQUESTS,
                    "otp_rate_limited", "Too many codes requested. Try again in an hour.");
        }

        String code = String.format("%06d", random.nextInt(1_000_000));
        redis.opsForValue().set(codeKey(phone), code, TTL);
        redis.delete(attemptKey(phone));

        // A real deployment hands this to the SMS provider. Here it goes to the log.
        log.info("OTP for {} is {}", phone, code);
        return new Issued(exposeCode ? code : null, (int) TTL.toSeconds());
    }

    public void verify(String phone, String code) {
        Long attempts = redis.opsForValue().increment(attemptKey(phone));
        if (attempts != null && attempts == 1L) redis.expire(attemptKey(phone), TTL);
        if (attempts != null && attempts > MAX_ATTEMPTS) {
            redis.delete(codeKey(phone));
            throw ApiException.badRequest("otp_locked", "Too many wrong codes. Request a new one.");
        }

        String expected = redis.opsForValue().get(codeKey(phone));
        if (expected == null) {
            throw ApiException.badRequest("otp_expired", "That code has expired. Request a new one.");
        }
        if (!constantTimeEquals(expected, code)) {
            throw ApiException.badRequest("otp_invalid", "That code is not right.");
        }
        redis.delete(codeKey(phone));
        redis.delete(attemptKey(phone));
    }

    private static boolean constantTimeEquals(String a, String b) {
        return java.security.MessageDigest.isEqual(
                a.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                b.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    private String codeKey(String phone) { return "otp:code:" + phone; }
    private String attemptKey(String phone) { return "otp:attempts:" + phone; }
}
