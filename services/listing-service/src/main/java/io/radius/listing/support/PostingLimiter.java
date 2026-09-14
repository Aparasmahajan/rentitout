package io.radius.listing.support;

import io.radius.common.web.ApiException;
import io.radius.listing.domain.MemberLocation;
import io.radius.listing.repo.MemberLocationRepository;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/**
 * How much a member may write, per hour.
 *
 * The build plan already calls for tighter limits under seven days, and
 * comments are the first thing that gets abused — so this ships with the
 * feature rather than after the spam. The gateway's limiter is per IP and
 * cannot tell a new account from an old one; this one is per member and
 * can.
 *
 * A fixed window rather than a sliding one: it is a spam brake, not a billing
 * meter, and the worst case is a member posting the hourly allowance twice
 * across a window boundary. That is still two orders of magnitude below what
 * makes a thread unreadable.
 */
@Component
public class PostingLimiter {

    /** Under this age, an account is new and gets the tighter bucket. */
    private static final Duration NEW_ACCOUNT = Duration.ofDays(7);

    private static final int COMMENTS_NEW = 5;
    private static final int COMMENTS_ESTABLISHED = 30;
    private static final int RATINGS_NEW = 3;
    private static final int RATINGS_ESTABLISHED = 15;

    private final StringRedisTemplate redis;
    private final MemberLocationRepository members;

    public PostingLimiter(StringRedisTemplate redis, MemberLocationRepository members) {
        this.redis = redis;
        this.members = members;
    }

    public void checkComment(UUID userId) {
        boolean fresh = isNewAccount(userId);
        check("comment", userId, fresh ? COMMENTS_NEW : COMMENTS_ESTABLISHED, fresh);
    }

    public void checkRating(UUID userId) {
        boolean fresh = isNewAccount(userId);
        check("rating", userId, fresh ? RATINGS_NEW : RATINGS_ESTABLISHED, fresh);
    }

    /**
     * Two different unknowns, and they mean opposite things.
     *
     * <p>No projection row at all means {@code UserRegistered} has not been
     * consumed yet — a Kafka round trip, so a second or two, and only ever in
     * the first seconds of an account's life. That is exactly the window the
     * tighter bucket exists for, so an unknown member counts as new. Reading it
     * the other way leaves a hole a spammer walks straight through: sign up,
     * post thirty times, and be gone before the event lands.
     *
     * <p>A row that exists with no date is a member who joined before the
     * column did. Those are established by definition.
     */
    private boolean isNewAccount(UUID userId) {
        MemberLocation member = members.findById(userId).orElse(null);
        if (member == null) return true;

        Instant registered = member.getRegisteredAt();
        return registered != null && registered.isAfter(Instant.now().minus(NEW_ACCOUNT));
    }

    private void check(String what, UUID userId, int perHour, boolean newAccount) {
        long hour = Instant.now().truncatedTo(ChronoUnit.HOURS).getEpochSecond();
        String key = "rate:" + what + ":" + userId + ":" + hour;

        Long count = redis.opsForValue().increment(key);
        if (count != null && count == 1L) {
            // Only on the first hit of the window, so a busy hour does not keep
            // pushing its own expiry out and leaking the key forever.
            redis.expire(key, Duration.ofHours(2));
        }
        if (count != null && count > perHour) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "posting_rate_limited", newAccount
                    ? "New accounts can post " + perHour + " an hour. Try again shortly."
                    : "That is " + perHour + " in an hour. Try again shortly.");
        }
    }
}
