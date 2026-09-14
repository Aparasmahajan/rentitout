package io.radius.common.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.Map;
import java.util.UUID;

/** Issues and verifies the access / refresh pair. Stateless on purpose. */
public class JwtService {

    public static final String TYPE_ACCESS = "access";
    public static final String TYPE_REFRESH = "refresh";

    private final JwtProperties props;
    private final SecretKey key;

    public JwtService(JwtProperties props) {
        this.props = props;
        byte[] secret = props.getSecret().getBytes(StandardCharsets.UTF_8);
        if (secret.length < 32) {
            throw new IllegalStateException("radius.jwt.secret must be at least 32 bytes");
        }
        this.key = Keys.hmacShaKeyFor(secret);
    }

    public String issueAccess(UUID userId, String displayName) {
        return issueAccess(userId, displayName, AuthUser.MEMBER);
    }

    public String issueAccess(UUID userId, String displayName, String role) {
        Instant now = Instant.now();
        return Jwts.builder()
                .issuer(props.getIssuer())
                .subject(userId.toString())
                .id(UUID.randomUUID().toString())
                .claims(Map.of("typ", TYPE_ACCESS,
                        "name", displayName == null ? "" : displayName,
                        "role", role == null ? AuthUser.MEMBER : role))
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(props.getAccessTtl())))
                .signWith(key)
                .compact();
    }

    /** The jti is stored server-side so a refresh token can be rotated and revoked. */
    public String issueRefresh(UUID userId, String tokenId) {
        Instant now = Instant.now();
        return Jwts.builder()
                .issuer(props.getIssuer())
                .subject(userId.toString())
                .id(tokenId)
                .claim("typ", TYPE_REFRESH)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(props.getRefreshTtl())))
                .signWith(key)
                .compact();
    }

    public Claims parse(String token) {
        return Jwts.parser().verifyWith(key).requireIssuer(props.getIssuer())
                .build().parseSignedClaims(token).getPayload();
    }

    /** Returns null instead of throwing — callers treat unverifiable tokens as anonymous. */
    public AuthUser verifyAccess(String token) {
        try {
            Claims c = parse(token);
            if (!TYPE_ACCESS.equals(c.get("typ", String.class))) return null;
            String role = c.get("role", String.class);
            return new AuthUser(UUID.fromString(c.getSubject()), c.get("name", String.class),
                    role == null ? AuthUser.MEMBER : role);
        } catch (JwtException | IllegalArgumentException e) {
            return null;
        }
    }

    public long accessTtlSeconds() { return props.getAccessTtl().getSeconds(); }

    public long refreshTtlSeconds() { return props.getRefreshTtl().getSeconds(); }
}
