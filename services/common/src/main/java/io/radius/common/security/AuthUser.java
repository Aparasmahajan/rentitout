package io.radius.common.security;

import io.radius.common.web.ApiException;

import java.util.UUID;

/**
 * Who is calling. The role is carried in the token so every service can answer
 * "may this person do that" without a round trip to user-service.
 *
 * Because it is in the token, a role change only takes effect on the next
 * access token — at most fifteen minutes. That is fine for promoting an admin
 * and wrong for revoking one: revoking also revokes the refresh family, which
 * ends the session immediately.
 */
public record AuthUser(UUID id, String displayName, String role) {

    public static final String MEMBER = "MEMBER";
    public static final String ADMIN = "ADMIN";

    public AuthUser(UUID id, String displayName) {
        this(id, displayName, MEMBER);
    }

    public boolean isAdmin() {
        return ADMIN.equals(role);
    }

    /** Throws rather than returning a boolean, so a forgotten check cannot fail open. */
    public void requireAdmin() {
        if (!isAdmin()) {
            throw ApiException.forbidden("That is an administrator action");
        }
    }
}
